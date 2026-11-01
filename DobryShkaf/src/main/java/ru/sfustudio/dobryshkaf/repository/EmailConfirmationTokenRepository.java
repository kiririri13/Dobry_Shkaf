package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sfustudio.dobryshkaf.domain.EmailConfirmationToken;
import ru.sfustudio.dobryshkaf.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {

    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
