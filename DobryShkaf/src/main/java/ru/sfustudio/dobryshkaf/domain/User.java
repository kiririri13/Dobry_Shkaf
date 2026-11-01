package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final int RATING_SCALE = 2;
    private static final BigDecimal MIN_RATING = BigDecimal.ONE;
    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(precision = 3, scale = RATING_SCALE)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public User(String email, String passwordHash, String name, UserRole role) {
        this.email = requireText(email, "email");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.name = requireText(name, "name");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public void rename(String name) {
        this.name = requireText(name, "name");
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
    }

    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public void updateRating(BigDecimal rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        BigDecimal rounded = rating.setScale(RATING_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(MIN_RATING) < 0 || rounded.compareTo(MAX_RATING) > 0) {
            throw new IllegalArgumentException("rating must be between %s and %s".formatted(MIN_RATING, MAX_RATING));
        }
        this.rating = rounded;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Через getId(): other может быть прокси Hibernate с незаполненными полями
        return other instanceof User user && id != null && id.equals(user.getId());
    }

    @Override
    public int hashCode() {
        // Постоянный хеш: id появляется только при сохранении и не должен менять корзину в коллекциях
        return User.class.hashCode();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
