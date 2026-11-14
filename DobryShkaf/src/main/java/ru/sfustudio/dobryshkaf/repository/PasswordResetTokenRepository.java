package ru.sfustudio.dobryshkaf.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.sfustudio.dobryshkaf.domain.PasswordResetToken;
import ru.sfustudio.dobryshkaf.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Блокировка не даёт двум одновременным запросам использовать одну ссылку дважды. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
