package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.ChatAttachment;
import ru.sfustudio.dobryshkaf.domain.ChatMessage;

import java.time.Instant;
import java.util.UUID;

/**
 * @param seq номер сообщения в диалоге; по нему догружается история и пропущенное после переподключения
 */
public record ChatMessageResponse(
        UUID id,
        UUID chatId,
        UUID senderId,
        long seq,
        String text,
        UUID requestId,
        Instant sentAt,
        Instant readAt,
        Attachment attachment
) {

    public static ChatMessageResponse from(ChatMessage message) {
        ChatAttachment file = message.getAttachment();
        Attachment attachment = file == null ? null : new Attachment(file.name(), file.sizeBytes(),
                "/api/chats/%s/messages/%s/attachment".formatted(message.getChat().getId(), message.getId()));
        return new ChatMessageResponse(
                message.getId(),
                message.getChat().getId(),
                message.getSender().getId(),
                message.getSeq(),
                message.getText(),
                message.getRequestId(),
                message.getCreatedAt(),
                message.getReadAt(),
                attachment);
    }

    public record Attachment(String name, int sizeBytes, String url) {
    }
}
