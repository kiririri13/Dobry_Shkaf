package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sfustudio.dobryshkaf.domain.TransferEvent;

import java.util.List;
import java.util.UUID;

public interface TransferEventRepository extends JpaRepository<TransferEvent, UUID> {

    List<TransferEvent> findByTransferIdOrderByCreatedAtAsc(UUID transferId);
}
