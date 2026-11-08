package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.dto.PointFilter;
import ru.sfustudio.dobryshkaf.dto.PointRequest;
import ru.sfustudio.dobryshkaf.dto.PointResponse;
import ru.sfustudio.dobryshkaf.service.PointService;

import java.util.List;
import java.util.UUID;

/** Управление пунктами; доступ только у роли ADMIN (см. SecurityConfig). */
@Tag(name = "4. Админка: пункты", description = "Только для роли ADMIN")
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointService pointService;

    @Operation(summary = "Все пункты, включая архивные")
    @GetMapping
    public List<PointResponse> search(
            @Valid @ParameterObject @ModelAttribute PointFilter filter,
            @RequestParam(required = false) PointStatus status) {
        return pointService.findAll(filter, status);
    }

    @Operation(summary = "Пункт по id, включая архивный")
    @GetMapping("/{id}")
    public PointResponse get(@PathVariable UUID id) {
        return pointService.get(id);
    }

    @Operation(summary = "Добавить пункт", description = "Для пункта обмена обязательны условия получения вещей")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PointResponse create(@Valid @RequestBody PointRequest request) {
        return pointService.create(request);
    }

    @Operation(summary = "Изменить пункт", description = "status: ACTIVE или TEMPORARILY_CLOSED (временно закрыт)")
    @PutMapping("/{id}")
    public PointResponse update(@PathVariable UUID id, @Valid @RequestBody PointRequest request) {
        return pointService.update(id, request);
    }

    @Operation(summary = "Убрать пункт в архив", description = "Пункт исчезает с карты и из списка, но не удаляется")
    @PostMapping("/{id}/archive")
    public PointResponse archive(@PathVariable UUID id) {
        return pointService.archive(id);
    }
}
