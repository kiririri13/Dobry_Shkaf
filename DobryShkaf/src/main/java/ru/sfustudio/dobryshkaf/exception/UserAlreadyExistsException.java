package ru.sfustudio.dobryshkaf.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {
        super("User with this email or phone already exists");
    }
}
