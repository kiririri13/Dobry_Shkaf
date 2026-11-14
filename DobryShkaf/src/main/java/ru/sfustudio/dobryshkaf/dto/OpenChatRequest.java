package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenChatRequest(
        @NotNull(message = "Укажите объявление")
        UUID itemId
) {
}
