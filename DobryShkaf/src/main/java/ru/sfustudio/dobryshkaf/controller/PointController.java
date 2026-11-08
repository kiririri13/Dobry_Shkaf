package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.dto.PointFilter;
import ru.sfustudio.dobryshkaf.dto.PointResponse;
import ru.sfustudio.dobryshkaf.service.PointService;

import java.util.List;
import java.util.UUID;

/** Один источник и для карты, и для списка: одинаковые фильтры всегда дают одинаковые пункты. */
@Tag(name = "3. Пункты: карта и список")
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "Пункты для карты и списка", description = "Архивные не показываются, временно закрытые помечены temporarilyClosed")
    @GetMapping
    public List<PointResponse> search(@Valid @ParameterObject @ModelAttribute PointFilter filter) {
        return pointService.findVisible(filter);
    }

    @Operation(summary = "Пункт по id")
    @GetMapping("/{id}")
    public PointResponse get(@PathVariable UUID id) {
        return pointService.getVisible(id);
    }
}
