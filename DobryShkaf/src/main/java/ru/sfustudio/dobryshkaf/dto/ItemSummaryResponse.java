package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.DeliveryMethod;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemStatus;

import java.util.UUID;

/** Краткие данные вещи для списка чатов и передач. */
public record ItemSummaryResponse(UUID id, String title, ItemStatus status, String photoUrl,
                                  DeliveryMethod deliveryMethod, String size) {

    public static ItemSummaryResponse from(Item item) {
        String photoUrl = item.getPhotos().isEmpty() ? null : ItemPhotoResponse.from(item.getPhotos().getFirst()).url();
        return new ItemSummaryResponse(item.getId(), item.getTitle(), item.getStatus(), photoUrl, item.getDeliveryMethod(), item.getSize());
    }
}
