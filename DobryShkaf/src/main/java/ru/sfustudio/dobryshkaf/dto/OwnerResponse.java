package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.User;

import java.math.BigDecimal;
import java.util.UUID;

/** Публичные данные автора объявления: почту и телефон посторонним не показываем. */
public record OwnerResponse(UUID id, String name, BigDecimal rating) {

    public static OwnerResponse from(User user) {
        return new OwnerResponse(user.getId(), user.getName(), user.getRating());
    }
}
