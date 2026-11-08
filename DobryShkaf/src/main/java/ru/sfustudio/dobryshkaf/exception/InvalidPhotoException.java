package ru.sfustudio.dobryshkaf.exception;

import lombok.Getter;

@Getter
public class InvalidPhotoException extends RuntimeException {

    private final String code;

    public InvalidPhotoException(String code, String message) {
        super(message);
        this.code = code;
    }
}
