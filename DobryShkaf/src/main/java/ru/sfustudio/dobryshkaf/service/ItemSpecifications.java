package ru.sfustudio.dobryshkaf.service;

import org.springframework.data.jpa.domain.Specification;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemStatus;
import ru.sfustudio.dobryshkaf.domain.ItemModerationStatus;
import ru.sfustudio.dobryshkaf.dto.ItemFilter;
import ru.sfustudio.dobryshkaf.exception.AuthenticationRequiredException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ItemSpecifications {

    private ItemSpecifications() {
    }

    static Specification<Item> matching(ItemFilter filter, Optional<CurrentUser> viewer) {
        List<Specification<Item>> specs = new ArrayList<>();
        if (filter.onlyMine()) {
            // Свои объявления владелец видит во всех статусах, включая черновики
            specs.add(ownedBy(viewer.orElseThrow(AuthenticationRequiredException::new).id()));
        } else {
            specs.add(publicListings(filter.available()));
        }
        if (hasText(filter.q())) {
            specs.add(titleContains(filter.q()));
        }
        if (filter.categoryId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("id"), filter.categoryId()));
        }
        if (filter.districtId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("district").get("id"), filter.districtId()));
        }
        if (filter.condition() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("condition"), filter.condition()));
        }
        if (hasText(filter.size())) {
            String size = filter.size().trim().toLowerCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.equal(cb.lower(root.get("size")), size));
        }
        return Specification.allOf(specs);
    }

    static Specification<Item> publicByOwner(UUID ownerId) {
        return publicListings(null).and(ownedBy(ownerId));
    }

    private static Specification<Item> publicListings(Boolean available) {
        return statusIn(visibleStatuses(available))
                .and((root, query, cb) -> cb.notEqual(root.get("moderationStatus"), ItemModerationStatus.HIDDEN));
    }

    private static Set<ItemStatus> visibleStatuses(Boolean available) {
        if (available == null) {
            return Item.PUBLIC_STATUSES;
        }
        return available ? Set.of(ItemStatus.PUBLISHED) : Set.of(ItemStatus.RESERVED);
    }

    private static Specification<Item> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    private static Specification<Item> statusIn(Collection<ItemStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    private static Specification<Item> titleContains(String text) {
        String pattern = LikePatterns.containsIgnoringCase(text);
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
