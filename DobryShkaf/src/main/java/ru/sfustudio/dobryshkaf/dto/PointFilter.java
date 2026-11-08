package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.domain.PointType;

import java.util.UUID;

/**
 * Общие фильтры карты и списка пунктов.
 *
 * @param q        часть названия или адреса
 * @param openOnly скрыть временно закрытые пункты
 */
public record PointFilter(
        PointType type,
        UUID categoryId,
        UUID districtId,
        @Size(max = 100, message = "Слишком длинный запрос")
        String q,
        Boolean openOnly
) {

    public boolean onlyOpen() {
        return Boolean.TRUE.equals(openOnly);
    }
}
