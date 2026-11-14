package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Токен сброса пароля; в базе хранится только SHA-256 от токена из письма. */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
