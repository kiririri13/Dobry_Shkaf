package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Не хватает токена из письма")
        String token,

        @NotBlank(message = "Укажите новый пароль")
        @Size(min = 8, message = "Пароль должен содержать не меньше 8 символов")
        String newPassword
) {

    // BCrypt учитывает только первые 72 байта, а кириллический символ занимает два
    private static final int MAX_PASSWORD_BYTES = 72;

    @AssertTrue(message = "Пароль слишком длинный")
    public boolean isPasswordWithinLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[]";
    }
}
