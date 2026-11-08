package ru.sfustudio.dobryshkaf.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public List<PointResponse> search(@Valid @ModelAttribute PointFilter filter) {
        return pointService.findVisible(filter);
    }

    @GetMapping("/{id}")
    public PointResponse get(@PathVariable UUID id) {
        return pointService.getVisible(id);
    }
}
