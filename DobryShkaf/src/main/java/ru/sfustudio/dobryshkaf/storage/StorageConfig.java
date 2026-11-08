package ru.sfustudio.dobryshkaf.storage;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    private static final int NOT_FOUND = 404;

    @Bean
    S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(properties.pathStyleAccess())
                // S3-совместимые хранилища не всегда понимают контрольные суммы, которые SDK добавляет по умолчанию
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.create-bucket", havingValue = "true")
    ApplicationRunner bucketInitializer(S3Client s3Client, StorageProperties properties) {
        return args -> {
            try {
                s3Client.headBucket(request -> request.bucket(properties.bucket()));
            } catch (S3Exception ex) {
                if (ex.statusCode() != NOT_FOUND) {
                    throw ex;
                }
                s3Client.createBucket(request -> request.bucket(properties.bucket()));
            }
        };
    }
}
