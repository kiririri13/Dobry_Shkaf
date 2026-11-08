package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;
import ru.sfustudio.dobryshkaf.dto.CategoryResponse;
import ru.sfustudio.dobryshkaf.dto.ConditionResponse;
import ru.sfustudio.dobryshkaf.dto.DistrictResponse;
import ru.sfustudio.dobryshkaf.service.ReferenceDataService;

import java.util.Arrays;
import java.util.List;

/** Справочники для формы объявления и фильтров каталога. */
@Tag(name = "0. Справочники", description = "id категорий и районов для форм и фильтров")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final ReferenceDataService referenceDataService;

    @Operation(summary = "Категории вещей")
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return referenceDataService.categories();
    }

    @Operation(summary = "Районы")
    @GetMapping("/districts")
    public List<DistrictResponse> districts() {
        return referenceDataService.districts();
    }

    @Operation(summary = "Состояния вещи")
    @GetMapping("/item-conditions")
    public List<ConditionResponse> conditions() {
        return Arrays.stream(ItemCondition.values()).map(ConditionResponse::from).toList();
    }
}
