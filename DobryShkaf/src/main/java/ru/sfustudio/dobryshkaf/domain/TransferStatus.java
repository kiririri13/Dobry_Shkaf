package ru.sfustudio.dobryshkaf.domain;

public enum TransferStatus {
    /** Получатель попросил вещь; вещь остаётся в каталоге. */
    REQUESTED,
    /** Владелец выбрал получателя; вещь в резерве. */
    RESERVED,
    /** Один участник подтвердил передачу, ждём второго. */
    AWAITING_CONFIRMATION,
    /** Один участник попросил отменить уже подтверждённую передачу; завершение приостановлено. */
    CANCELLATION_REQUESTED,
    COMPLETED,
    REJECTED,
    CANCELLED
}
