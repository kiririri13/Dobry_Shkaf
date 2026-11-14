package ru.sfustudio.dobryshkaf.service;

/**
 * Результат команды, которую можно безопасно повторить.
 *
 * @param created {@code false}, если это повтор и вернулся уже существующий объект
 */
public record CommandResult<T>(T body, boolean created) {
}
