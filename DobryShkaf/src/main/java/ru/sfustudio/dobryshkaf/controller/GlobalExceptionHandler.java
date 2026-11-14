package ru.sfustudio.dobryshkaf.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.sfustudio.dobryshkaf.dto.ErrorResponse;
import ru.sfustudio.dobryshkaf.exception.AuthenticationRequiredException;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.InvalidConfirmationTokenException;
import ru.sfustudio.dobryshkaf.exception.InvalidPasswordResetTokenException;
import ru.sfustudio.dobryshkaf.exception.InvalidPhotoException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.exception.UserAlreadyExistsException;
import ru.sfustudio.dobryshkaf.exception.UserNotFoundException;
import ru.sfustudio.dobryshkaf.service.ItemService;

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

    @ExceptionHandler(FieldValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleFieldValidation(FieldValidationException ex) {
        return new ErrorResponse(ex.getCode(), ex.getMessage(), ex.getFields());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedRequest() {
        return ErrorResponse.of("MALFORMED_REQUEST", "Некорректный запрос");
    }

    @ExceptionHandler(InvalidPhotoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidPhoto(InvalidPhotoException ex) {
        return ErrorResponse.of(ex.getCode(), ex.getMessage());
    }

    // Tomcat отклоняет слишком большой файл ещё до контроллера; ответ тот же, что и при проверке в сервисе
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUploadTooLarge() {
        InvalidPhotoException ex = ItemService.photoTooLarge();
        return ErrorResponse.of(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserAlreadyExists() {
        return ErrorResponse.of("USER_ALREADY_EXISTS", "Пользователь с такой почтой или телефоном уже зарегистрирован");
    }

    // Два одновременных запроса прошли проверку в коде, и сработало ограничение БД, например уникальный индекс
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation() {
        return ErrorResponse.of("CONFLICT", "Данные изменились одновременно с другим запросом, повторите попытку");
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException ex) {
        return ErrorResponse.of(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException ex) {
        return ErrorResponse.of("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException ex) {
        return ErrorResponse.of("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthenticationRequired() {
        return ErrorResponse.of("UNAUTHORIZED", "Войдите в аккаунт");
    }

    @ExceptionHandler(InvalidConfirmationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidConfirmationToken() {
        return ErrorResponse.of("INVALID_CONFIRMATION_TOKEN", "Ссылка недействительна или устарела");
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidPasswordResetToken() {
        return ErrorResponse.of("INVALID_RESET_TOKEN", "Ссылка для сброса пароля недействительна, уже использована или устарела");
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
        // Ошибка преобразования (например, не UUID) содержит технический текст, его пользователю не показываем
        if (error.isBindingFailure() || error.getDefaultMessage() == null) {
            return "Некорректное значение";
        }
        return error.getDefaultMessage();
    }
}
