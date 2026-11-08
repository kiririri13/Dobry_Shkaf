package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.District;

import java.util.UUID;

public record DistrictResponse(UUID id, String city, String name) {

    public static DistrictResponse from(District district) {
        return new DistrictResponse(district.getId(), district.getCity(), district.getName());
    }
}
