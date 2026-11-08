package ru.sfustudio.dobryshkaf.controller;

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
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return referenceDataService.categories();
    }

    @GetMapping("/districts")
    public List<DistrictResponse> districts() {
        return referenceDataService.districts();
    }

    @GetMapping("/item-conditions")
    public List<ConditionResponse> conditions() {
        return Arrays.stream(ItemCondition.values()).map(ConditionResponse::from).toList();
    }
}
