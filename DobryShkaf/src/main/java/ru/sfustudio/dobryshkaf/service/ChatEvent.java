package ru.sfustudio.dobryshkaf.service;

/** Сообщение, которое получают подписчики темы диалога через WebSocket. */
public record ChatEvent(ChatEventType type, Object payload) {
}
