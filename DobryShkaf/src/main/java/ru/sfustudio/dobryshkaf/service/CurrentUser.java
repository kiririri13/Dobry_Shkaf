package ru.sfustudio.dobryshkaf.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.UserRole;
import ru.sfustudio.dobryshkaf.exception.AuthenticationRequiredException;

import java.util.Optional;
import java.util.UUID;

/** Вошедший пользователь; имя в {@link Authentication} — его id (см. DatabaseUserDetailsService). */
public record CurrentUser(UUID id, boolean admin) {

    private static final String ADMIN_AUTHORITY = "ROLE_" + UserRole.ADMIN.name();

    public static Optional<CurrentUser> from(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return Optional.of(new CurrentUser(UUID.fromString(authentication.getName()), admin));
    }

    public static CurrentUser require(Authentication authentication) {
        return from(authentication).orElseThrow(AuthenticationRequiredException::new);
    }

    /** Менять объявление может владелец или администратор. */
    public boolean canManage(Item item) {
        return admin || item.isOwnedBy(id);
    }
}
