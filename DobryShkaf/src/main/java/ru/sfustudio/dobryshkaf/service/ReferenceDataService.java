package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.dto.CategoryResponse;
import ru.sfustudio.dobryshkaf.dto.DistrictResponse;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private final CategoryRepository categoryRepository;
    private final DistrictRepository districtRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DistrictResponse> districts() {
        return districtRepository.findAllByOrderByCityAscNameAsc().stream().map(DistrictResponse::from).toList();
    }
}
