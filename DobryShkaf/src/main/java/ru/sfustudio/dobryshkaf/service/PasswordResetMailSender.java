package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;
import ru.sfustudio.dobryshkaf.config.AppProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetMailSender {

    private static final String SUBJECT = "Сброс пароля в «Добром шкафе»";

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    /** Асинхронно: время ответа на запрос сброса не должно выдавать, существует ли почта. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(event.email());
        message.setSubject(SUBJECT);
        message.setText(body(event));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("failed_send_password_reset_mail userId={}", event.userId(), ex);
        }
    }

    private String body(PasswordResetRequested event) {
        String link = UriComponentsBuilder.fromUriString(properties.passwordResetUrl())
                .queryParam("token", event.token())
                .toUriString();
        return """
                Здравствуйте, %s!

                Чтобы задать новый пароль в «Добром шкафе», перейдите по ссылке:
                %s

                Ссылка действует %d минут и срабатывает один раз.
                Если вы не просили сбросить пароль, просто проигнорируйте это письмо.
                """.formatted(event.name(), link, properties.passwordResetTokenTtl().toMinutes());
    }
}
