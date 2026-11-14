package ru.sfustudio.dobryshkaf.service;

import java.util.UUID;

/** Внутреннее событие Spring: отправить {@link ChatEvent} в тему диалога после фиксации транзакции. */
public record ChatEventPublished(UUID chatId, ChatEvent event) {
}
