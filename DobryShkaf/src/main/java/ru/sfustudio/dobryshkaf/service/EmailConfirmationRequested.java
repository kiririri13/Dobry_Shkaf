package ru.sfustudio.dobryshkaf.service;

import java.util.UUID;

public record EmailConfirmationRequested(UUID userId, String email, String name, String token) {
}
