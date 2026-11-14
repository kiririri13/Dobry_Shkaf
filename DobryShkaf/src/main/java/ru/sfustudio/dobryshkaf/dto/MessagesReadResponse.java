package ru.sfustudio.dobryshkaf.dto;

import java.util.UUID;

/** Событие «сообщения прочитаны»: второй участник видит отметку прочтения у своих сообщений. */
public record MessagesReadResponse(UUID readerId, long upToSeq) {
}
