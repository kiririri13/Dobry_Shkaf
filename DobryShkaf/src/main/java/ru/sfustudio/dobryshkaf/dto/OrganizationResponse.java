package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.Organization;

import java.util.UUID;

public record OrganizationResponse(UUID id, String name) {

    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName());
    }
}
