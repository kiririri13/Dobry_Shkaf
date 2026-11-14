package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param upToSeq номер последнего прочитанного сообщения */
public record MarkReadRequest(
        @NotNull(message = "Укажите номер сообщения")
        @Min(value = 1, message = "Номер сообщения начинается с 1")
        Long upToSeq
) {
}
