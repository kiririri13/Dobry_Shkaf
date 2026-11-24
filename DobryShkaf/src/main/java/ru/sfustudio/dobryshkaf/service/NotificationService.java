package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 20;

    private final JdbcClient jdbc;

    void item(Item item, Type type, String detail) {
        create(item.getOwner().getId(), type, item.getId(), detail);
    }

    void create(UUID userId, Type type, UUID entityId, String detail) {
        jdbc.sql("insert into notifications (user_id, type, entity_id, detail) values (?, ?, ?, ?)")
                .params(userId, type.name(), entityId, detail).update();
    }

    void removeReviewReceived(UUID reviewId) {
        jdbc.sql("delete from notifications where type = 'REVIEW_RECEIVED' and entity_id = ?")
                .param(reviewId).update();
    }

    @Transactional(readOnly = true)
    public Feed list(CurrentUser user, boolean unreadOnly, int page) {
        List<Entry> entries = jdbc.sql("""
                select id, type, entity_id, detail, created_at, read_at
                from notifications where user_id = ? and (not ? or read_at is null)
                order by created_at desc, id desc limit ? offset ?
                """).params(user.id(), unreadOnly, PAGE_SIZE + 1, PAGE_SIZE * (long) page).query(Entry.class).list();
        boolean hasMore = entries.size() > PAGE_SIZE;
        long unreadCount = jdbc.sql("select count(*) from notifications where user_id = ? and read_at is null")
                .param(user.id()).query(Long.class).single();
        return new Feed(hasMore ? entries.subList(0, PAGE_SIZE) : entries, unreadCount, hasMore);
    }

    @Transactional
    public void markRead(CurrentUser user, UUID id) {
        int updated = jdbc.sql("""
                update notifications set read_at = coalesce(read_at, now()) where user_id = ? and id = ?
                """).params(user.id(), id).update();
        if (updated == 0) {
            throw new NotFoundException("Уведомление не найдено");
        }
    }

    @Transactional
    public void markReviewRead(CurrentUser user, UUID id) {
        int updated = jdbc.sql("""
                update notifications set read_at = coalesce(read_at, now())
                where user_id = ? and type = 'REVIEW_RECEIVED' and entity_id = ?
                """).params(user.id(), id).update();
        if (updated == 0) {
            throw new NotFoundException("Полученный отзыв не найден");
        }
    }

    @Transactional
    public void markAllRead(CurrentUser user) {
        jdbc.sql("update notifications set read_at = now() where user_id = ? and read_at is null")
                .param(user.id()).update();
    }

    public enum Type {
        REVIEW_RECEIVED, REVIEW_HIDDEN,
        ITEM_PUBLISHED, ITEM_UNPUBLISHED, ITEM_REVIEWED, ITEM_HIDDEN, ITEM_RESTORED,
        COMPLAINT_SUBMITTED, COMPLAINT_UPHELD, COMPLAINT_DISMISSED, COMPLAINT_REOPENED,
        ACCOUNT_BLOCKED
    }

    public record Entry(UUID id, Type type, UUID entityId, String detail, OffsetDateTime createdAt, OffsetDateTime readAt) {
    }

    public record Feed(List<Entry> items, long unreadCount, boolean hasMore) {
    }
}
