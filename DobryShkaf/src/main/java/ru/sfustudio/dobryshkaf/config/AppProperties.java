package ru.sfustudio.dobryshkaf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, String mailFrom, Duration confirmationTokenTtl) {
}
