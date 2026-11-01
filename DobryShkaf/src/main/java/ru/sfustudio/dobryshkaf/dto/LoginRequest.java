package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.NotBlank;

/** @param login почта или телефон */
public record LoginRequest(
        @NotBlank(message = "Укажите почту или телефон")
        String login,

        @NotBlank(message = "Укажите пароль")
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest[login=%s]".formatted(login);
    }
}
