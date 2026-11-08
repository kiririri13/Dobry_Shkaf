package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;
import ru.sfustudio.dobryshkaf.domain.ItemStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String title,
        String description,
        ItemCondition condition,
        String size,
        ItemStatus status,
        CategoryResponse category,
        DistrictResponse district,
        OwnerResponse owner,
        List<ItemPhotoResponse> photos,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCondition(),
                item.getSize(),
                item.getStatus(),
                item.getCategory() == null ? null : CategoryResponse.from(item.getCategory()),
                item.getDistrict() == null ? null : DistrictResponse.from(item.getDistrict()),
                OwnerResponse.from(item.getOwner()),
                item.getPhotos().stream().map(ItemPhotoResponse::from).toList(),
                item.getPublishedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
