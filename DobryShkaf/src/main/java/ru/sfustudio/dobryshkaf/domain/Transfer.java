package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.sfustudio.dobryshkaf.exception.ConflictException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Передача вещи от владельца получателю. Переходы состояний — по таблице 5 ТЗ.
 * Каждая команда возвращает {@code false}, если это повтор и состояние уже такое, каким его делает команда:
 * повтор возвращает прежний результат, а не ошибку.
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    /** Передача ещё идёт: у получателя может быть только одна такая на вещь. */
    public static final Set<TransferStatus> ACTIVE_STATUSES = EnumSet.of(
            TransferStatus.REQUESTED, TransferStatus.RESERVED,
            TransferStatus.AWAITING_CONFIRMATION, TransferStatus.CANCELLATION_REQUESTED);

    /** Получатель выбран: у вещи может быть только одна такая передача. */
    private static final Set<TransferStatus> SELECTED_STATUSES = EnumSet.of(
            TransferStatus.RESERVED, TransferStatus.AWAITING_CONFIRMATION,
            TransferStatus.CANCELLATION_REQUESTED, TransferStatus.COMPLETED);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "giver_id", nullable = false, updatable = false)
    private User giver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false, updatable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, updatable = false)
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransferStatus status;

    private Instant giverConfirmedAt;

    private Instant receiverConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TransferRole cancellationRequestedBy;

    private Instant completedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Transfer(Item item, User receiver, Chat chat) {
        if (!item.isAvailable()) {
            throw new ConflictException("ITEM_NOT_AVAILABLE", "Вещь сейчас недоступна для запроса");
        }
        if (item.isOwnedBy(receiver.getId())) {
            throw new ConflictException("OWN_ITEM", "Нельзя запросить собственную вещь");
        }
        this.item = item;
        this.giver = item.getOwner();
        this.receiver = Objects.requireNonNull(receiver, "receiver must not be null");
        this.chat = Objects.requireNonNull(chat, "chat must not be null");
        this.status = TransferStatus.REQUESTED;
    }

    public Optional<TransferRole> roleOf(UUID userId) {
        if (giver.getId().equals(userId)) {
            return Optional.of(TransferRole.GIVER);
        }
        if (receiver.getId().equals(userId)) {
            return Optional.of(TransferRole.RECEIVER);
        }
        return Optional.empty();
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(status);
    }

    public boolean isConfirmedBy(TransferRole role) {
        return (role == TransferRole.GIVER ? giverConfirmedAt : receiverConfirmedAt) != null;
    }

    /** Запрошена → Резерв. Остальные запросы на вещь отклоняет сервис через {@link #rejectBecauseClosed()}. */
    public boolean select() {
        if (status == TransferStatus.REQUESTED) {
            item.reserve();
            status = TransferStatus.RESERVED;
            return true;
        }
        if (SELECTED_STATUSES.contains(status)) {
            return false;
        }
        throw conflict("Запрос уже закрыт, выбрать этого получателя нельзя");
    }

    /** Запрошена → Отклонена: выбран другой получатель или объявление закрыто. */
    public boolean rejectBecauseClosed() {
        if (status == TransferStatus.REQUESTED) {
            status = TransferStatus.REJECTED;
            return true;
        }
        return false;
    }

    /**
     * После резерва получатель подтверждает получение, затем владелец подтверждает передачу.
     * Повтор подтверждения тем же участником ничего не меняет.
     * Завершение в одной транзакции отмечает и передачу, и вещь.
     */
    public boolean confirm(TransferRole by, Instant now) {
        switch (status) {
            case RESERVED -> {
                if (by != TransferRole.RECEIVER) {
                    throw conflict("Сначала получатель должен отметить, что забрал вещь");
                }
                markConfirmed(by, now);
                status = TransferStatus.AWAITING_CONFIRMATION;
                return true;
            }
            case AWAITING_CONFIRMATION -> {
                if (isConfirmedBy(by)) {
                    return false;
                }
                markConfirmed(by, now);
                status = TransferStatus.COMPLETED;
                completedAt = now;
                item.markTransferred();
                return true;
            }
            case COMPLETED -> {
                return false;
            }
            case CANCELLATION_REQUESTED -> throw conflict("Идёт согласование отмены, подтвердить передачу сейчас нельзя");
            default -> throw conflict("Передачу в этом состоянии подтвердить нельзя");
        }
    }

    /**
     * Запрошена: владелец отклоняет, получатель отзывает. Резерв: отмена, вещь возвращается в каталог.
     * Ожидает второго: отмену нужно согласовать со вторым участником.
     */
    public boolean cancel(TransferRole by) {
        switch (status) {
            case REQUESTED -> {
                status = by == TransferRole.GIVER ? TransferStatus.REJECTED : TransferStatus.CANCELLED;
                return true;
            }
            case RESERVED -> {
                status = TransferStatus.CANCELLED;
                item.releaseReservation();
                return true;
            }
            case AWAITING_CONFIRMATION -> {
                status = TransferStatus.CANCELLATION_REQUESTED;
                cancellationRequestedBy = by;
                return true;
            }
            case CANCELLATION_REQUESTED -> {
                if (cancellationRequestedBy == by) {
                    return false;
                }
                throw conflict("Другой участник уже попросил отменить передачу: примите или отклоните его просьбу");
            }
            case CANCELLED, REJECTED -> {
                return false;
            }
            default -> throw conflict("Завершённую передачу отменить нельзя");
        }
    }

    /** Отмена запрошена → Отменена: второй участник согласился, вещь снова в каталоге. */
    public boolean acceptCancellation(TransferRole by) {
        if (status == TransferStatus.CANCELLATION_REQUESTED) {
            requireOtherThanRequester(by);
            status = TransferStatus.CANCELLED;
            item.releaseReservation();
            return true;
        }
        if (status == TransferStatus.CANCELLED) {
            return false;
        }
        throw conflict("Отмену никто не запрашивал");
    }

    /** Отмена запрошена → Ожидает второго подтверждения: второй участник не согласился. */
    public boolean declineCancellation(TransferRole by) {
        if (status == TransferStatus.CANCELLATION_REQUESTED) {
            requireOtherThanRequester(by);
            status = TransferStatus.AWAITING_CONFIRMATION;
            cancellationRequestedBy = null;
            return true;
        }
        if (status == TransferStatus.AWAITING_CONFIRMATION) {
            return false;
        }
        throw conflict("Отмену никто не запрашивал");
    }

    /** Что может сделать участник с этой ролью прямо сейчас; фронтенду по нему удобно показывать кнопки. */
    public Set<TransferAction> availableActions(TransferRole role) {
        Set<TransferAction> actions = EnumSet.noneOf(TransferAction.class);
        if (role == null) {
            return actions;
        }
        switch (status) {
            case REQUESTED -> {
                if (role == TransferRole.GIVER) {
                    actions.add(TransferAction.SELECT);
                }
                actions.add(TransferAction.CANCEL);
            }
            case RESERVED -> {
                if (role == TransferRole.RECEIVER) {
                    actions.add(TransferAction.CONFIRM);
                }
                actions.add(TransferAction.CANCEL);
            }
            case AWAITING_CONFIRMATION -> {
                if (!isConfirmedBy(role)) {
                    actions.add(TransferAction.CONFIRM);
                }
                actions.add(TransferAction.CANCEL);
            }
            case CANCELLATION_REQUESTED -> {
                if (role != cancellationRequestedBy) {
                    actions.add(TransferAction.ACCEPT_CANCELLATION);
                    actions.add(TransferAction.DECLINE_CANCELLATION);
                }
            }
            default -> {
                // Передача завершена, отклонена или отменена
            }
        }
        return actions;
    }

    private void markConfirmed(TransferRole by, Instant now) {
        if (by == TransferRole.GIVER) {
            giverConfirmedAt = now;
        } else {
            receiverConfirmedAt = now;
        }
    }

    private void requireOtherThanRequester(TransferRole by) {
        if (by == cancellationRequestedBy) {
            throw conflict("Ответить на просьбу об отмене должен другой участник");
        }
    }

    private static ConflictException conflict(String message) {
        return new ConflictException("TRANSFER_STATE_CONFLICT", message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Transfer transfer && id != null && id.equals(transfer.getId());
    }

    @Override
    public int hashCode() {
        return Transfer.class.hashCode();
    }
}
