package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ru.sfustudio.dobryshkaf.exception.InvalidPhotoException;
import ru.sfustudio.dobryshkaf.storage.PhotoStorage;

import java.io.IOException;
import java.io.UncheckedIOException;

@Component
@RequiredArgsConstructor
@Slf4j
class MediaFiles {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final PhotoStorage storage;

    byte[] imageBytes(MultipartFile file) {
        return read(file, "EMPTY_PHOTO", "Фото пустое", ItemService.photoTooLarge());
    }

    byte[] attachmentBytes(MultipartFile file) {
        return read(file, "EMPTY_ATTACHMENT", "Вложение пустое",
                new InvalidPhotoException("ATTACHMENT_TOO_LARGE", "Вложение должно быть не больше 5 МБ"));
    }

    ImageFormat imageFormat(byte[] bytes) {
        return ImageFormat.detect(bytes)
                .orElseThrow(() -> new InvalidPhotoException("UNSUPPORTED_PHOTO_FORMAT",
                        "Поддерживаются только фото в форматах JPEG, PNG и WebP"));
    }

    void put(String key, byte[] bytes, String contentType) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(key);
                }
            }
        });
        storage.put(key, bytes, contentType);
    }

    byte[] get(String key) {
        return storage.get(key);
    }

    void deleteAfterCommit(String key) {
        if (key != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("failed_delete_media key={}", key, ex);
        }
    }

    private static byte[] read(MultipartFile file, String emptyCode, String emptyMessage, InvalidPhotoException tooLarge) {
        if (file.isEmpty()) {
            throw new InvalidPhotoException(emptyCode, emptyMessage);
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge;
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file", ex);
        }
    }
}
