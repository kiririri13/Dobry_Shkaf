package ru.sfustudio.dobryshkaf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param passwordResetUrl страница фронтенда, на которую ведёт ссылка из письма о сбросе пароля
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        String mailFrom,
        Duration confirmationTokenTtl,
        String passwordResetUrl,
        Duration passwordResetTokenTtl
) {
}
