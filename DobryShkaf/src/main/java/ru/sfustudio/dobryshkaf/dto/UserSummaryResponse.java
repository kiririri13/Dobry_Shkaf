package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.User;

import java.util.UUID;

/** Имя участника без почты и телефона. */
public record UserSummaryResponse(UUID id, String name) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getName());
    }
}
