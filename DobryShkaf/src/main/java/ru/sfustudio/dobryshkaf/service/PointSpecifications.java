package ru.sfustudio.dobryshkaf.service;

import org.springframework.data.jpa.domain.Specification;
import ru.sfustudio.dobryshkaf.domain.CollectionPoint;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.dto.PointFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class PointSpecifications {

    private PointSpecifications() {
    }

    static Specification<CollectionPoint> matching(PointFilter filter, Collection<PointStatus> statuses) {
        List<Specification<CollectionPoint>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> root.get("status").in(statuses));
        if (filter.onlyOpen()) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), PointStatus.ACTIVE));
        }
        if (filter.type() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("type"), filter.type()));
        }
        if (filter.districtId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("district").get("id"), filter.districtId()));
        }
        if (filter.categoryId() != null) {
            specs.add((root, query, cb) -> {
                // Соединение с категориями размножило бы строки, если категорий несколько
                query.distinct(true);
                return cb.equal(root.join("categories").get("id"), filter.categoryId());
            });
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String pattern = LikePatterns.containsIgnoringCase(filter.q());
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, LikePatterns.ESCAPE),
                    cb.like(cb.lower(root.get("address")), pattern, LikePatterns.ESCAPE)));
        }
        return Specification.allOf(specs);
    }
}
