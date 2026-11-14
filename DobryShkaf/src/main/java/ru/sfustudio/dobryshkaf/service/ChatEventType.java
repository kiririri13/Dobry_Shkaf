package ru.sfustudio.dobryshkaf.service;

public enum ChatEventType {
    /** payload — {@code ChatMessageResponse} */
    MESSAGE_CREATED,
    /** payload — {@code MessagesReadResponse} */
    MESSAGES_READ,
    /** payload — {@code TransferResponse} без {@code myRole} и {@code availableActions} */
    TRANSFER_UPDATED
}
