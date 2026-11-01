package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.service.PhoneNumbers;

import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        @Size(max = 254, message = "Почта слишком длинная")
        String email,

        String phone,

        @NotBlank(message = "Укажите имя")
        @Size(max = 100, message = "Имя слишком длинное")
        String name,

        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, message = "Пароль должен содержать не меньше 8 символов")
        String password
) {

    // BCrypt учитывает только первые 72 байта, а кириллический символ занимает два
    private static final int MAX_PASSWORD_BYTES = 72;

    @AssertTrue(message = "Пароль слишком длинный")
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    @AssertTrue(message = "Некорректный номер телефона")
    public boolean isPhoneValid() {
        return phone == null || PhoneNumbers.normalize(phone).isPresent();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=%s]".formatted(email);
    }
}
