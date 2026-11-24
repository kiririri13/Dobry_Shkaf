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
import ru.sfustudio.dobryshkaf.config.AppProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class ListingMessageMailSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyOwner(ListingMessageReceived event) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mailFrom());
        mail.setTo(event.ownerEmail());
        mail.setSubject("Новое сообщение по вашему объявлению в «Добром шкафе»");
        mail.setText("""
                Пользователь %s написал вам по объявлению «%s».

                Откройте диалог в «Добром шкафе», чтобы прочитать сообщение и ответить.
                """.formatted(event.senderName(), event.itemTitle()));
        try {
            mailSender.send(mail);
        } catch (MailException ex) {
            log.warn("failed_send_listing_message_mail chatId={} error={}", event.chatId(), ex.getClass().getSimpleName());
        }
    }
}
