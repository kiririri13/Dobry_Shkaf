package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    /** id вещи без загрузки самой передачи: её нужно читать уже после блокировки вещи. */
    @Query("select t.item.id from Transfer t where t.id = :id")
    Optional<UUID> findItemIdById(@Param("id") UUID id);

    Optional<Transfer> findFirstByItemIdAndReceiverIdAndStatusIn(UUID itemId, UUID receiverId,
                                                                 Collection<TransferStatus> statuses);

    List<Transfer> findByItemIdAndStatus(UUID itemId, TransferStatus status);

    List<Transfer> findByItemIdOrderByCreatedAtDesc(UUID itemId);

    List<Transfer> findByItemIdAndReceiverIdOrderByCreatedAtDesc(UUID itemId, UUID receiverId);

    @Query("select t from Transfer t where t.giver.id = :userId or t.receiver.id = :userId order by t.updatedAt desc")
    List<Transfer> findAllForParticipant(@Param("userId") UUID userId);
}
