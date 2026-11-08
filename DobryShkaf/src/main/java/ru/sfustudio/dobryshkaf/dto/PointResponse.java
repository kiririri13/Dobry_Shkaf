package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.CollectionPoint;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.domain.PointType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PointResponse(
        UUID id,
        PointType type,
        String name,
        OrganizationResponse organization,
        String address,
        DistrictResponse district,
        BigDecimal latitude,
        BigDecimal longitude,
        String hours,
        String contacts,
        List<CategoryResponse> categories,
        String conditions,
        String receivingConditions,
        String source,
        LocalDate verifiedAt,
        PointStatus status,
        boolean temporarilyClosed,
        Instant updatedAt
) {

    public static PointResponse from(CollectionPoint point) {
        return new PointResponse(
                point.getId(),
                point.getType(),
                point.getName(),
                OrganizationResponse.from(point.getOrganization()),
                point.getAddress(),
                DistrictResponse.from(point.getDistrict()),
                point.getLatitude(),
                point.getLongitude(),
                point.getHours(),
                point.getContacts(),
                point.getCategories().stream()
                        .sorted(Comparator.comparingInt(Category::getSortOrder))
                        .map(CategoryResponse::from)
                        .toList(),
                point.getConditions(),
                point.getReceivingConditions(),
                point.getSource(),
                point.getVerifiedAt(),
                point.getStatus(),
                point.isTemporarilyClosed(),
                point.getUpdatedAt());
    }
}
