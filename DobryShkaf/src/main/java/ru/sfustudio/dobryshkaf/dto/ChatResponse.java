package ru.sfustudio.dobryshkaf.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param counterpart второй участник диалога (для владельца — заинтересованный пользователь, и наоборот)
 * @param unreadCount сколько сообщений от второго участника вы ещё не прочитали
 */
public record ChatResponse(
        UUID id,
        ItemSummaryResponse item,
        UserSummaryResponse counterpart,
        ChatMessageResponse lastMessage,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
