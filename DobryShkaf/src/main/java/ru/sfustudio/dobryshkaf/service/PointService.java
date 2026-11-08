package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.CollectionPoint;
import ru.sfustudio.dobryshkaf.domain.District;
import ru.sfustudio.dobryshkaf.domain.Organization;
import ru.sfustudio.dobryshkaf.domain.PointDetails;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.dto.PointFilter;
import ru.sfustudio.dobryshkaf.dto.PointRequest;
import ru.sfustudio.dobryshkaf.dto.PointResponse;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.CollectionPointRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.OrganizationRepository;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointService {

    /** Карта и список показывают одно и то же: действующие и временно закрытые пункты. */
    private static final Set<PointStatus> VISIBLE_STATUSES = EnumSet.of(PointStatus.ACTIVE, PointStatus.TEMPORARILY_CLOSED);
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final CollectionPointRepository pointRepository;
    private final OrganizationRepository organizationRepository;
    private final CategoryRepository categoryRepository;
    private final DistrictRepository districtRepository;

    @Transactional(readOnly = true)
    public List<PointResponse> findVisible(PointFilter filter) {
        return find(filter, VISIBLE_STATUSES);
    }

    @Transactional(readOnly = true)
    public PointResponse getVisible(UUID id) {
        return pointRepository.findById(id)
                .filter(CollectionPoint::isVisible)
                .map(PointResponse::from)
                .orElseThrow(PointService::pointNotFound);
    }

    /** Для администратора, включая архивные. */
    @Transactional(readOnly = true)
    public List<PointResponse> findAll(PointFilter filter, PointStatus status) {
        return find(filter, status == null ? EnumSet.allOf(PointStatus.class) : EnumSet.of(status));
    }

    @Transactional(readOnly = true)
    public PointResponse get(UUID id) {
        return PointResponse.from(findPoint(id));
    }

    @Transactional
    public PointResponse create(PointRequest request) {
        CollectionPoint point = new CollectionPoint(details(request));
        if (request.status() != null) {
            point.changeStatus(request.status());
        }
        return respond(pointRepository.save(point));
    }

    @Transactional
    public PointResponse update(UUID id, PointRequest request) {
        CollectionPoint point = findPoint(id);
        point.update(details(request));
        if (request.status() != null) {
            point.changeStatus(request.status());
        }
        return respond(point);
    }

    @Transactional
    public PointResponse archive(UUID id) {
        CollectionPoint point = findPoint(id);
        point.archive();
        return respond(point);
    }

    private List<PointResponse> find(PointFilter filter, Set<PointStatus> statuses) {
        return pointRepository.findAll(PointSpecifications.matching(filter, statuses), BY_NAME).stream()
                .map(PointResponse::from)
                .toList();
    }

    private CollectionPoint findPoint(UUID id) {
        return pointRepository.findById(id).orElseThrow(PointService::pointNotFound);
    }

    private PointDetails details(PointRequest request) {
        return new PointDetails(
                request.type(),
                request.name().trim(),
                organization(request.organization().trim()),
                district(request.districtId()),
                request.address().trim(),
                request.latitude(),
                request.longitude(),
                request.hours().trim(),
                request.contacts().trim(),
                categories(request.categoryIds()),
                request.conditions().trim(),
                request.receivingConditions() == null ? null : request.receivingConditions().trim(),
                request.source().trim(),
                request.verifiedAt());
    }

    private Organization organization(String name) {
        return organizationRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> organizationRepository.save(new Organization(name)));
    }

    private District district(UUID id) {
        return districtRepository.findById(id).orElseThrow(() -> invalidReference("districtId", "Район не найден"));
    }

    private Set<Category> categories(Set<UUID> ids) {
        List<Category> found = categoryRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw invalidReference("categoryIds", "Категория не найдена");
        }
        return new HashSet<>(found);
    }

    private PointResponse respond(CollectionPoint point) {
        // Время создания и изменения Hibernate проставляет при записи в БД
        pointRepository.flush();
        return PointResponse.from(point);
    }

    private static NotFoundException pointNotFound() {
        return new NotFoundException("Пункт не найден");
    }

    private static FieldValidationException invalidReference(String field, String message) {
        return new FieldValidationException("VALIDATION_FAILED", "Проверьте заполнение полей", Map.of(field, message));
    }
}
