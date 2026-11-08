package ru.sfustudio.dobryshkaf.exception;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("Authentication required");
    }
}
