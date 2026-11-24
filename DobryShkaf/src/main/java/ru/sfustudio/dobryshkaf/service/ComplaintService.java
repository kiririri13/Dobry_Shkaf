package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Chat;
import ru.sfustudio.dobryshkaf.domain.ChatMessage;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.domain.UserRole;
import ru.sfustudio.dobryshkaf.dto.ItemSummaryResponse;
import ru.sfustudio.dobryshkaf.dto.MessagePageResponse;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.dto.UserSummaryResponse;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.ChatMessageRepository;
import ru.sfustudio.dobryshkaf.repository.ChatRepository;
import ru.sfustudio.dobryshkaf.repository.CollectionPointRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.TransferRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    public static final String CHAT_NOTICE = "Модератор сможет прочитать весь этот диалог для рассмотрения жалобы";

    private final JdbcClient jdbc;
    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final ItemRepository items;
    private final UserRepository users;
    private final CollectionPointRepository points;
    private final TransferRepository transfers;
    private final ChatService chatService;
    private final ModerationLog log;
    private final NotificationService notifications;

    @Transactional
    public Complaint create(TargetType type, UUID id, String reason, CurrentUser reporter) {
        UUID reportedOwnerId = null;
        UUID chatId = switch (type) {
            case CHAT -> verifyParticipant(findChat(id), reporter.id());
            case MESSAGE -> verifyParticipant(messages.findById(id).map(ChatMessage::getChat)
                    .orElseThrow(ComplaintService::targetNotFound), reporter.id());
            case ITEM -> {
                reportedOwnerId = items.findById(id).filter(item -> item.isPublic() || item.isOwnedBy(reporter.id()))
                        .orElseThrow(ComplaintService::targetNotFound).getOwner().getId();
                yield null;
            }
            case POINT -> {
                points.findById(id).filter(point -> point.getStatus() != PointStatus.ARCHIVED)
                        .orElseThrow(ComplaintService::targetNotFound);
                yield null;
            }
            case PROFILE -> {
                if (!users.existsById(id)) {
                    throw targetNotFound();
                }
                reportedOwnerId = id;
                yield null;
            }
        };
        Complaint complaint = jdbc.sql("""
                insert into complaints (target_type, target_id, reason, reporter_id, chat_id)
                values (?, ?, ?, ?, ?) returning *
                """).params(type.name(), id, reason, reporter.id(), chatId).query(Complaint.class).single();
        notifications.create(reporter.id(), NotificationService.Type.COMPLAINT_SUBMITTED, complaint.id(), null);
        if (reportedOwnerId != null && !reportedOwnerId.equals(reporter.id())) {
            NotificationService.Type notification = type == TargetType.ITEM
                    ? NotificationService.Type.ITEM_REPORTED : NotificationService.Type.PROFILE_REPORTED;
            notifications.create(reportedOwnerId, notification, id, null);
        }
        return complaint;
    }

    public Complaint submitted(UUID id, CurrentUser reporter) {
        return jdbc.sql("select * from complaints where id = ? and reporter_id = ?")
                .params(id, reporter.id()).query(Complaint.class).optional()
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
    }

    public List<Complaint> list(Status status, int page, CurrentUser actor) {
        requireModerator(actor);
        return jdbc.sql("""
                select * from complaints where cast(:status as varchar) is null or status = :status
                order by created_at desc, id desc limit 20 offset :offset
                """).param("status", status == null ? null : status.name()).param("offset", 20L * page)
                .query(Complaint.class).list();
    }

    public Complaint get(UUID id, CurrentUser actor) {
        requireModerator(actor);
        return find(id, false);
    }

    @Transactional
    public Complaint close(UUID id, Outcome outcome, String explanation, CurrentUser actor) {
        requireModerator(actor);
        Complaint complaint = find(id, true);
        Complaint closed = jdbc.sql("""
                update complaints set status = 'CLOSED', outcome = ?, explanation = ?, updated_at = now()
                where id = ? and status = 'OPEN' returning *
                """).params(outcome.name(), explanation, id).query(Complaint.class).optional().orElse(complaint);
        if (complaint.status() == Status.OPEN) {
            log.recordDecision(actor, complaint.targetType().name(), complaint.targetId(), id, outcome, explanation);
            NotificationService.Type type = outcome == Outcome.UPHELD
                    ? NotificationService.Type.COMPLAINT_UPHELD : NotificationService.Type.COMPLAINT_DISMISSED;
            notifications.create(complaint.reporterId(), type, id, explanation);
        }
        return closed;
    }

    @Transactional
    public Complaint reopen(UUID id, CurrentUser actor) {
        requireModerator(actor);
        Complaint complaint = find(id, true);
        Complaint reopened = jdbc.sql("""
                update complaints set status = 'OPEN', outcome = null, explanation = null, updated_at = now()
                where id = ? and status = 'CLOSED' returning *
                """).param(id).query(Complaint.class).optional().orElse(complaint);
        if (complaint.status() == Status.CLOSED) {
            log.record(actor, complaint.targetType().name(), complaint.targetId(), "REOPEN_COMPLAINT", null, id);
            notifications.create(complaint.reporterId(), NotificationService.Type.COMPLAINT_REOPENED, id, null);
        }
        return reopened;
    }

    @Transactional
    public Conversation history(UUID complaintId, UUID chatId, Long beforeSeq, Long afterSeq, Integer limit,
                                CurrentUser actor) {
        requireModerator(actor);
        Complaint complaint = find(complaintId, true);
        if (complaint.status() != Status.OPEN || !chatId.equals(complaint.chatId())) {
            throw new ForbiddenException("Для этого диалога нужна открытая жалоба его участника");
        }
        Chat chat = findChat(chatId);
        verifyParticipant(chat, complaint.reporterId());
        MessagePageResponse page = chatService.messagePage(chatId, beforeSeq, afterSeq, limit);
        List<TransferResponse> related = transfers.findByChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(transfer -> TransferResponse.from(transfer, null)).toList();
        log.record(actor, "CHAT", chatId, "VIEW_CHAT", null, complaintId);
        return new Conversation(chatId, ItemSummaryResponse.from(chat.getItem()),
                List.of(UserSummaryResponse.from(chat.getItem().getOwner()), UserSummaryResponse.from(chat.getRequester())),
                related, page);
    }

    private Complaint find(UUID id, boolean lock) {
        return jdbc.sql("select * from complaints where id = ?" + (lock ? " for update" : ""))
                .param(id).query(Complaint.class).optional()
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
    }

    private Chat findChat(UUID id) {
        return chats.findById(id).orElseThrow(ComplaintService::targetNotFound);
    }

    private static UUID verifyParticipant(Chat chat, UUID reporter) {
        if (!chat.isParticipant(reporter)) {
            throw new ForbiddenException("Подать жалобу на переписку может только участник диалога");
        }
        return chat.getId();
    }

    private void requireModerator(CurrentUser actor) {
        if (!actor.canModerate() || !users.existsByIdAndBlockedFalseAndRoleIn(actor.id(),
                List.of(UserRole.ADMIN, UserRole.MODERATOR))) {
            throw new ForbiddenException("Нужна роль модератора");
        }
    }

    private static NotFoundException targetNotFound() {
        return new NotFoundException("Объект жалобы не найден");
    }

    public enum TargetType { ITEM, POINT, PROFILE, MESSAGE, CHAT }
    public enum Status { OPEN, CLOSED }
    public enum Outcome { UPHELD, DISMISSED }

    public record Complaint(UUID id, UUID reporterId, TargetType targetType, UUID targetId, UUID chatId,
                            String reason, Status status, Outcome outcome, String explanation,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record Conversation(UUID chatId, ItemSummaryResponse item, List<UserSummaryResponse> participants,
                               List<TransferResponse> transfers, MessagePageResponse history) {
    }
}
