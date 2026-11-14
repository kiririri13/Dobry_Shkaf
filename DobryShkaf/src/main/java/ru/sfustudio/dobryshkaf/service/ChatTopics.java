package ru.sfustudio.dobryshkaf.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Темы STOMP, на которые подписывается фронтенд: {@code /topic/chats.{chatId}}. */
public final class ChatTopics {

    private static final String PREFIX = "/topic/chats.";
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chats\\.([0-9a-fA-F-]{36})$");

    private ChatTopics() {
    }

    public static String of(UUID chatId) {
        return PREFIX + chatId;
    }

    public static Optional<UUID> chatIdOf(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = CHAT_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
