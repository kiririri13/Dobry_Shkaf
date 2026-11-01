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
public class ConfirmationMailSender {

    private static final String SUBJECT = "Подтвердите регистрацию в «Добром шкафе»";

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailConfirmationRequested(EmailConfirmationRequested event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(event.email());
        message.setSubject(SUBJECT);
        message.setText(body(event));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Регистрация уже сохранена: пользователь сможет запросить письмо повторно
            log.error("failed_send_confirmation_mail userId={}", event.userId(), ex);
        }
    }

    private String body(EmailConfirmationRequested event) {
        String link = UriComponentsBuilder.fromUriString(properties.publicUrl())
                .path("/api/auth/confirm")
                .queryParam("token", event.token())
                .toUriString();
        return """
                Здравствуйте, %s!

                Чтобы завершить регистрацию в «Добром шкафе», перейдите по ссылке:
                %s

                Ссылка действует %d ч. Если вы не регистрировались, просто проигнорируйте это письмо.
                """.formatted(event.name(), link, properties.confirmationTokenTtl().toHours());
    }
}
