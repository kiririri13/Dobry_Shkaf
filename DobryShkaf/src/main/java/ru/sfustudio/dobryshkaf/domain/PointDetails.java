package ru.sfustudio.dobryshkaf.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Данные карточки пункта, которые заполняет администратор. */
public record PointDetails(
        PointType type,
        String name,
        Organization organization,
        District district,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String hours,
        String contacts,
        Set<Category> categories,
        String conditions,
        String receivingConditions,
        String source,
        LocalDate verifiedAt
) {
}
