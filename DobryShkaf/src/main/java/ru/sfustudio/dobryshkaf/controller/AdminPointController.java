package ru.sfustudio.dobryshkaf.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointService pointService;

    @GetMapping
    public List<PointResponse> search(
            @Valid @ModelAttribute PointFilter filter,
            @RequestParam(required = false) PointStatus status) {
        return pointService.findAll(filter, status);
    }

    @GetMapping("/{id}")
    public PointResponse get(@PathVariable UUID id) {
        return pointService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PointResponse create(@Valid @RequestBody PointRequest request) {
        return pointService.create(request);
    }

    @PutMapping("/{id}")
    public PointResponse update(@PathVariable UUID id, @Valid @RequestBody PointRequest request) {
        return pointService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public PointResponse archive(@PathVariable UUID id) {
        return pointService.archive(id);
    }
}
