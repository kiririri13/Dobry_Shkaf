package ru.sfustudio.dobryshkaf.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Формат определяется по сигнатуре в начале файла: имени и Content-Type от клиента верить нельзя,
 * их легко подменить.
 */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_FOURCC = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final int WEBP_FOURCC_OFFSET = 8;

    private final String contentType;
    private final String extension;

    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, WEBP_FOURCC_OFFSET, WEBP_FOURCC)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
