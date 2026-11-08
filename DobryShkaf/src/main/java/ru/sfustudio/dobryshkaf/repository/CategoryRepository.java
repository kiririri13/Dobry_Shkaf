package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sfustudio.dobryshkaf.domain.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAsc();
}
