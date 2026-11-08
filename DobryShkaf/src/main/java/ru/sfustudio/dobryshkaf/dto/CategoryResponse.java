package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String code, String name, boolean sizeRequired) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.isSizeRequired());
    }
}
