package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.domain.TransferRole;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.TransferRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final JdbcClient jdbc;
    private final TransferRepository transfers;
    private final UserRepository users;
    private final ModerationLog log;

    @Transactional
    public Review create(UUID transferId, CurrentUser author, int rating, String text) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> new NotFoundException("Передача не найдена"));
        TransferRole role = transfer.roleOf(author.id())
                .orElseThrow(() -> new ForbiddenException("Вы не участник этой передачи"));
        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new ConflictException("TRANSFER_NOT_COMPLETED", "Оставить отзыв можно после завершения передачи");
        }
        UUID target = role == TransferRole.GIVER ? transfer.getReceiver().getId() : transfer.getGiver().getId();
        User reviewed = users.findForUpdateById(target).orElseThrow();
        Review review = jdbc.sql("""
                insert into reviews (transfer_id, author_id, target_user_id, rating, text) values (?, ?, ?, ?, ?)
                on conflict (transfer_id, author_id) do nothing
                returning id, transfer_id, author_id, target_user_id, rating, text, created_at
                """).params(transferId, author.id(), target, rating, text).query(Review.class).optional()
                .orElseThrow(() -> new ConflictException("REVIEW_EXISTS", "Вы уже оставили отзыв по этой передаче"));
        updateRating(reviewed);
        return review;
    }

    public List<PublicReview> list(UUID target, int page) {
        return jdbc.sql("""
                select id, author_id, rating, text, created_at
                from reviews where target_user_id = ? and not hidden
                order by created_at desc, id desc limit 20 offset ?
                """).params(target, 20L * page).query(PublicReview.class).list();
    }

    @Transactional
    public void hide(UUID id, String reason, CurrentUser actor) {
        UUID target = jdbc.sql("select target_user_id from reviews where id = ?").param(id).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("Отзыв не найден"));
        User reviewed = users.findForUpdateById(target).orElseThrow();
        if (jdbc.sql("update reviews set hidden = true where id = ? and not hidden").param(id).update() != 0) {
            updateRating(reviewed);
            log.record(actor, "REVIEW", id, "HIDE_REVIEW", reason, null);
        }
    }

    private void updateRating(User reviewed) {
        BigDecimal average = jdbc.sql("select avg(rating) from reviews where target_user_id = ? and not hidden")
                .param(reviewed.getId()).query(BigDecimal.class).optional().orElse(null);
        if (average == null) {
            reviewed.clearRating();
        } else {
            reviewed.updateRating(average);
        }
    }

    public record PublicReview(UUID id, UUID authorId, int rating, String text, OffsetDateTime createdAt) {
    }

    public record Review(UUID id, UUID transferId, UUID authorId, UUID targetUserId, int rating, String text,
                         OffsetDateTime createdAt) {
    }
}
