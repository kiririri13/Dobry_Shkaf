package ru.sfustudio.dobryshkaf.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.sfustudio.dobryshkaf.dto.ErrorResponse;
import ru.sfustudio.dobryshkaf.exception.InvalidConfirmationTokenException;
import ru.sfustudio.dobryshkaf.exception.UserAlreadyExistsException;
import ru.sfustudio.dobryshkaf.exception.UserNotFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, GlobalExceptionHandler::message, (first, second) -> first));
        return new ErrorResponse("VALIDATION_FAILED", "Проверьте заполнение полей", fields);
    }

    // DataIntegrityViolationException: два одновременных запроса прошли проверку, сработал уникальный индекс
    @ExceptionHandler({UserAlreadyExistsException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserAlreadyExists() {
        return ErrorResponse.of("USER_ALREADY_EXISTS", "Пользователь с такой почтой или телефоном уже зарегистрирован");
    }

    @ExceptionHandler(InvalidConfirmationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidConfirmationToken() {
        return ErrorResponse.of("INVALID_CONFIRMATION_TOKEN", "Ссылка недействительна или устарела");
    }

    @ExceptionHandler({BadCredentialsException.class, UserNotFoundException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        return ErrorResponse.of("INVALID_CREDENTIALS", "Неверный логин или пароль");
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleEmailNotConfirmed() {
        return ErrorResponse.of("EMAIL_NOT_CONFIRMED", "Подтвердите почту по ссылке из письма");
    }

    private static String message(FieldError error) {
        return error.getDefaultMessage() == null ? "Некорректное значение" : error.getDefaultMessage();
    }
}
