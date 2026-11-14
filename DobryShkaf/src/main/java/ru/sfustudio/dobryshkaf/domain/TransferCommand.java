package ru.sfustudio.dobryshkaf.domain;

/** Команды передачи; та же запись попадает в журнал событий. */
public enum TransferCommand {
    REQUEST,
    SELECT,
    /** Запрос отклонён автоматически, потому что владелец выбрал другого получателя или закрыл объявление. */
    AUTO_REJECT,
    CONFIRM,
    CANCEL,
    ACCEPT_CANCELLATION,
    DECLINE_CANCELLATION
}
