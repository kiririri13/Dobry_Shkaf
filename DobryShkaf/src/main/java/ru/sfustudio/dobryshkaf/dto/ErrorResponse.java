package ru.sfustudio.dobryshkaf.dto;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, String> fields) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }
}
