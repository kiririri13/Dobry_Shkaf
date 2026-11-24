package ru.sfustudio.dobryshkaf.service;

import java.util.UUID;

record ListingMessageReceived(UUID chatId, String ownerEmail, String itemTitle, String senderName) {
}
