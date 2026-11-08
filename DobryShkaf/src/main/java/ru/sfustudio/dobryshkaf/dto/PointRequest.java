package ru.sfustudio.dobryshkaf.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.domain.PointType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Карточка пункта от администратора.
 *
 * @param organization название организации; если такой ещё нет, она будет создана
 * @param status       {@code ACTIVE} или {@code TEMPORARILY_CLOSED}; не указан — при создании действующий,
 *                     при изменении остаётся прежним
 */
public record PointRequest(
        @NotNull(message = "Укажите тип пункта")
        PointType type,

        @NotBlank(message = "Укажите название")
        @Size(max = 200, message = "Название слишком длинное")
        String name,

        @NotBlank(message = "Укажите организацию")
        @Size(max = 200, message = "Название организации слишком длинное")
        String organization,

        @NotBlank(message = "Укажите адрес")
        @Size(max = 300, message = "Адрес слишком длинный")
        String address,

        @NotNull(message = "Укажите район")
        UUID districtId,

        @NotNull(message = "Укажите широту")
        @DecimalMin(value = "-90", message = "Широта должна быть от -90 до 90")
        @DecimalMax(value = "90", message = "Широта должна быть от -90 до 90")
        BigDecimal latitude,

        @NotNull(message = "Укажите долготу")
        @DecimalMin(value = "-180", message = "Долгота должна быть от -180 до 180")
        @DecimalMax(value = "180", message = "Долгота должна быть от -180 до 180")
        BigDecimal longitude,

        @NotBlank(message = "Укажите часы работы")
        @Size(max = 500, message = "Слишком длинное описание часов работы")
        String hours,

        @NotBlank(message = "Укажите контакты")
        @Size(max = 500, message = "Слишком длинные контакты")
        String contacts,

        @NotEmpty(message = "Укажите хотя бы одну категорию")
        Set<@NotNull UUID> categoryIds,

        @NotBlank(message = "Укажите условия приёма")
        @Size(max = 2000, message = "Условия слишком длинные")
        String conditions,

        @Size(max = 2000, message = "Условия получения слишком длинные")
        String receivingConditions,

        @NotBlank(message = "Укажите источник данных")
        @Size(max = 300, message = "Источник слишком длинный")
        String source,

        @NotNull(message = "Укажите дату проверки")
        @PastOrPresent(message = "Дата проверки не может быть в будущем")
        LocalDate verifiedAt,

        PointStatus status
) {

    @AssertTrue(message = "Для пункта обмена укажите условия получения вещей")
    public boolean isReceivingConditionsProvided() {
        return type != PointType.EXCHANGE || (receivingConditions != null && !receivingConditions.isBlank());
    }

    @AssertTrue(message = "Чтобы убрать пункт в архив, используйте отдельную команду")
    public boolean isStatusAllowed() {
        return status != PointStatus.ARCHIVED;
    }
}
