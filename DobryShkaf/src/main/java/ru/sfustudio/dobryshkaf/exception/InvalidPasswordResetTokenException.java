package ru.sfustudio.dobryshkaf.exception;

public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Password reset token is invalid, used or expired");
    }
}
