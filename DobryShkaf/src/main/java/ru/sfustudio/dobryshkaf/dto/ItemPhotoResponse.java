package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.ItemPhoto;

import java.util.UUID;

public record ItemPhotoResponse(UUID id, String url, String contentType, int sortOrder) {

    public static ItemPhotoResponse from(ItemPhoto photo) {
        return new ItemPhotoResponse(photo.getId(), urlFor(photo.getId()), photo.getContentType(), photo.getSortOrder());
    }

    public static String urlFor(UUID id) {
        return "/api/photos/" + id;
    }
}
