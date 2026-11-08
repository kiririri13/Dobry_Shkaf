package ru.sfustudio.dobryshkaf.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * @param endpoint      адрес S3-совместимого хранилища; {@code null} — Amazon S3
 * @param createBucket  создать бакет при старте, если его нет (удобно для локальной разработки)
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        URI endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess,
        boolean createBucket
) {
}
