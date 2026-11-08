package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;

import java.util.UUID;

/** Черновик можно сохранить с одним названием; остальные поля обязательны только для публикации. */
public record ItemRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 100, message = "Название слишком длинное")
        String title,

        @Size(max = 2000, message = "Описание слишком длинное")
        String description,

        ItemCondition condition,

        @Size(max = 20, message = "Размер слишком длинный")
        String size,

        UUID categoryId,

        UUID districtId
) {
}
