package ru.sfustudio.dobryshkaf.service;

import java.util.UUID;

public record PasswordResetRequested(UUID userId, String email, String name, String token) {
}
