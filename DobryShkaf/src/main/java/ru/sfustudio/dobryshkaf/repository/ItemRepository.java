package ru.sfustudio.dobryshkaf.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sfustudio.dobryshkaf.domain.Item;

import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    /** Блокировка строки не даёт двум параллельным загрузкам превысить лимит фото. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findForUpdateById(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"owner", "category", "district"})
    Page<Item> findAll(Specification<Item> spec, Pageable pageable);
}
