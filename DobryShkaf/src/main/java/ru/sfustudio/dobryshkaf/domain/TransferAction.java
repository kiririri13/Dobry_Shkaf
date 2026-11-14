package ru.sfustudio.dobryshkaf.domain;

/** Действия, которые участник может выполнить с передачей сейчас. */
public enum TransferAction {
    SELECT,
    CONFIRM,
    CANCEL,
    ACCEPT_CANCELLATION,
    DECLINE_CANCELLATION
}
