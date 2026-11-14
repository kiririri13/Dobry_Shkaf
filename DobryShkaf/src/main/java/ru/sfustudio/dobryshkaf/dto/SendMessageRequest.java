package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.domain.ChatMessage;

import java.util.UUID;

/**
 * @param requestId случайный UUID, который фронтенд создаёт для каждого нового сообщения
 *                  и повторяет при повторной отправке: так сообщение не задвоится
 */
public record SendMessageRequest(
        @NotBlank(message = "Сообщение не может быть пустым")
        @Size(max = ChatMessage.MAX_TEXT_LENGTH, message = "Сообщение длиннее 2000 символов")
        String text,

        @NotNull(message = "Укажите идентификатор запроса")
        UUID requestId
) {
}
