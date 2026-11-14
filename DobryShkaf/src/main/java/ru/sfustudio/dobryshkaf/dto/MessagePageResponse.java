package ru.sfustudio.dobryshkaf.dto;

import java.util.List;

/**
 * @param messages сообщения от старых к новым
 * @param hasMore  есть ли ещё сообщения в направлении запроса
 */
public record MessagePageResponse(List<ChatMessageResponse> messages, boolean hasMore) {
}
