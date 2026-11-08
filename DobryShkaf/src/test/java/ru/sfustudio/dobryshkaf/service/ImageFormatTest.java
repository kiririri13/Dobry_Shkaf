package ru.sfustudio.dobryshkaf.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    @Test
    void detectsJpeg() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00))).contains(ImageFormat.JPEG);
    }

    @Test
    void detectsPng() {
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00))).contains(ImageFormat.PNG);
    }

    @Test
    void detectsWebp() {
        assertThat(ImageFormat.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsOtherRiffFormats() {
        assertThat(ImageFormat.detect(ascii("RIFF\0\0\0\0WAVEfmt "))).isEmpty();
    }

    @Test
    void rejectsTextDisguisedAsImage() {
        assertThat(ImageFormat.detect(ascii("<html>not an image</html>"))).isEmpty();
    }

    @Test
    void rejectsTooShortContent() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
