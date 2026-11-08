package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sfustudio.dobryshkaf.domain.ItemPhoto;

import java.util.Optional;
import java.util.UUID;

public interface ItemPhotoRepository extends JpaRepository<ItemPhoto, UUID> {

    @EntityGraph(attributePaths = "item")
    Optional<ItemPhoto> findWithItemById(UUID id);
}
