package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.ItemPhoto;

import java.util.UUID;

public record ItemPhotoResponse(UUID id, String url, String contentType, int sortOrder) {

    public static ItemPhotoResponse from(ItemPhoto photo) {
        return new ItemPhotoResponse(photo.getId(), "/api/photos/" + photo.getId(), photo.getContentType(), photo.getSortOrder());
    }
}
