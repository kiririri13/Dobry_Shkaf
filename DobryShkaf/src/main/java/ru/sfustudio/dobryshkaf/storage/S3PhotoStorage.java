package ru.sfustudio.dobryshkaf.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@RequiredArgsConstructor
public class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3Client.putObject(
                request -> request.bucket(properties.bucket()).key(key).contentType(contentType),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3Client.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(key)).asByteArray();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }
}
