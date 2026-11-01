package ru.sfustudio.dobryshkaf.exception;

public class InvalidConfirmationTokenException extends RuntimeException {

    public InvalidConfirmationTokenException() {
        super("Confirmation token is invalid or expired");
    }
}
