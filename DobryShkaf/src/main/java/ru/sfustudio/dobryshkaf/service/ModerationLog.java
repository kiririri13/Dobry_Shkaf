package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ModerationLog {

    private final JdbcClient jdbc;

    void record(CurrentUser actor, String targetType, UUID targetId, String action, String reason, UUID complaintId) {
        jdbc.sql("""
                insert into moderation_log (actor_id, target_type, target_id, action, reason, complaint_id)
                values (?, ?, ?, ?, ?, ?)
                """).params(actor.id(), targetType, targetId, action, reason, complaintId).update();
    }

    void recordDecision(CurrentUser actor, String targetType, UUID targetId, UUID complaintId,
                        ComplaintService.Outcome outcome, String explanation) {
        jdbc.sql("""
                insert into moderation_log (actor_id, target_type, target_id, action, reason, complaint_id, outcome)
                values (?, ?, ?, 'CLOSE_COMPLAINT', ?, ?, ?)
                """).params(actor.id(), targetType, targetId, explanation, complaintId, outcome.name()).update();
    }

    public List<Entry> list(int page) {
        return jdbc.sql("""
                select * from moderation_log order by created_at desc, id desc limit 20 offset ?
                """).param(20L * page).query(Entry.class).list();
    }

    public record Entry(UUID id, UUID actorId, String targetType, UUID targetId, String action, String reason,
                        UUID complaintId, ComplaintService.Outcome outcome, OffsetDateTime createdAt) {
    }
}
