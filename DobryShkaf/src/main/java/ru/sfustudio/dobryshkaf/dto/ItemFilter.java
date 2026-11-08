package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;

import java.util.UUID;

/**
 * Параметры поиска в каталоге.
 *
 * @param q         часть названия
 * @param available {@code true} — только доступные, {@code false} — только зарезервированные
 * @param mine      свои объявления во всех статусах, включая черновики
 */
public record ItemFilter(
        @Size(max = 100, message = "Слишком длинный запрос")
        String q,
        UUID categoryId,
        UUID districtId,
        ItemCondition condition,
        @Size(max = 20, message = "Размер слишком длинный")
        String size,
        Boolean available,
        Boolean mine,
        @Min(value = 0, message = "Номер страницы не может быть отрицательным")
        Integer page
) {

    public boolean onlyMine() {
        return Boolean.TRUE.equals(mine);
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }
}
