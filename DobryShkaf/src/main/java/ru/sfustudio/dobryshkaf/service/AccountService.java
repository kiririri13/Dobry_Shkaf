package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.config.AppProperties;
import ru.sfustudio.dobryshkaf.domain.EmailConfirmationToken;
import ru.sfustudio.dobryshkaf.domain.PasswordResetToken;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.domain.UserRole;
import ru.sfustudio.dobryshkaf.dto.RegisterRequest;
import ru.sfustudio.dobryshkaf.exception.InvalidConfirmationTokenException;
import ru.sfustudio.dobryshkaf.exception.InvalidPasswordResetTokenException;
import ru.sfustudio.dobryshkaf.exception.UserAlreadyExistsException;
import ru.sfustudio.dobryshkaf.exception.UserNotFoundException;
import ru.sfustudio.dobryshkaf.repository.EmailConfirmationTokenRepository;
import ru.sfustudio.dobryshkaf.repository.PasswordResetTokenRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final EmailConfirmationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    @Transactional
    public User register(RegisterRequest request) {
        String phone = request.phone() == null ? null : PhoneNumbers.normalize(request.phone()).orElseThrow();
        if (userRepository.existsByEmailIgnoreCase(request.email())
                || (phone != null && userRepository.existsByPhone(phone))) {
            throw new UserAlreadyExistsException();
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()), request.name(), UserRole.USER);
        user.changePhone(phone);
        userRepository.save(user);
        issueConfirmation(user);
        return user;
    }

    @Transactional
    public void confirmEmail(String token) {
        EmailConfirmationToken confirmation = tokenRepository.findByTokenHash(ConfirmationTokens.hash(token))
                .filter(found -> !found.isExpired(Instant.now()))
                .orElseThrow(InvalidConfirmationTokenException::new);

        User user = confirmation.getUser();
        user.confirmEmail();
        tokenRepository.deleteByUser(user);
    }

    @Transactional
    public void resendConfirmation(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isEmailConfirmed())
                .ifPresent(this::issueConfirmation);
    }

    /**
     * Для неизвестной почты ничего не делает, а ответ контроллера одинаковый: по нему нельзя узнать,
     * зарегистрирован ли адрес. Новая ссылка отменяет предыдущие.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            resetTokenRepository.deleteByUser(user);
            String token = ConfirmationTokens.generate();
            Instant expiresAt = Instant.now().plus(properties.passwordResetTokenTtl());
            resetTokenRepository.save(new PasswordResetToken(user, ConfirmationTokens.hash(token), expiresAt));
            events.publishEvent(new PasswordResetRequested(user.getId(), user.getEmail(), user.getName(), token));
        });
    }

    /** Ссылка срабатывает один раз: после смены пароля все токены пользователя удаляются. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken reset = resetTokenRepository.findByTokenHash(ConfirmationTokens.hash(token))
                .filter(found -> !found.isExpired(Instant.now()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        User user = reset.getUser();
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        // Письмо дошло до владельца почты, значит, почта подтверждена
        user.confirmEmail();
        resetTokenRepository.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    private void issueConfirmation(User user) {
        tokenRepository.deleteByUser(user);
        String token = ConfirmationTokens.generate();
        Instant expiresAt = Instant.now().plus(properties.confirmationTokenTtl());
        tokenRepository.save(new EmailConfirmationToken(user, ConfirmationTokens.hash(token), expiresAt));
        events.publishEvent(new EmailConfirmationRequested(user.getId(), user.getEmail(), user.getName(), token));
    }
}
