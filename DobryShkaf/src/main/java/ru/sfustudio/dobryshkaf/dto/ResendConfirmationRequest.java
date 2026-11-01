package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        String email
) {
}
