package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.domain.UserRole;

import java.util.UUID;

public record UserResponse(UUID id, String email, String phone, String name, UserRole role, boolean emailConfirmed) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getName(),
                user.getRole(),
                user.isEmailConfirmed());
    }
}
