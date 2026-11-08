package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.sfustudio.dobryshkaf.domain.CollectionPoint;

import java.util.List;
import java.util.UUID;

public interface CollectionPointRepository
        extends JpaRepository<CollectionPoint, UUID>, JpaSpecificationExecutor<CollectionPoint> {

    @Override
    @EntityGraph(attributePaths = {"organization", "district"})
    List<CollectionPoint> findAll(Specification<CollectionPoint> spec, Sort sort);
}
