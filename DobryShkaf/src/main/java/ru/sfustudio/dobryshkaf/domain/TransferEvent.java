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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Запись журнала передачи: кто и какой командой изменил её состояние. */
@Entity
@Table(name = "transfer_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TransferCommand command;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private TransferStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TransferStatus toStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public TransferEvent(Transfer transfer, User actor, TransferCommand command,
                         TransferStatus fromStatus, TransferStatus toStatus) {
        this.transfer = transfer;
        this.actor = actor;
        this.command = command;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
}
