package ru.sfustudio.dobryshkaf.exception;

import lombok.Getter;

import java.util.Map;

/** Ошибка в данных, которую нельзя выразить аннотациями Bean Validation, например ссылка на несуществующую категорию. */
@Getter
public class FieldValidationException extends RuntimeException {

    private final String code;
    private final Map<String, String> fields;

    public FieldValidationException(String code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = Map.copyOf(fields);
    }
}
