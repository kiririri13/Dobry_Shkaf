package ru.sfustudio.dobryshkaf.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfirmationTokenTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-11-02T12:00:00Z");

    private final EmailConfirmationToken token = new EmailConfirmationToken(
            new User("anna@example.com", "hash", "Анна", UserRole.USER), "token-hash", EXPIRES_AT);

    @Test
    void isValidBeforeExpiry() {
        assertThat(token.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
    }

    @Test
    void isExpiredAtExpiryMoment() {
        assertThat(token.isExpired(EXPIRES_AT)).isTrue();
    }
}
