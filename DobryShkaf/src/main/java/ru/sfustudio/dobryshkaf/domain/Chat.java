package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;
import java.util.UUID;

/** Диалог по вещи между её владельцем и одним заинтересованным пользователем. */
@Entity
@Table(name = "chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    /** Номер последнего сообщения; по номерам фронтенд догружает историю и пропущенное. */
    @Column(nullable = false)
    private long lastSeq;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Chat(Item item, User requester) {
        this.item = Objects.requireNonNull(item, "item must not be null");
        this.requester = Objects.requireNonNull(requester, "requester must not be null");
    }

    public boolean isParticipant(UUID userId) {
        return requester.getId().equals(userId) || item.isOwnedBy(userId);
    }

    public User counterpartOf(UUID userId) {
        return requester.getId().equals(userId) ? item.getOwner() : requester;
    }

    /** Вызывать только под блокировкой строки чата, иначе два сообщения получат один номер. */
    long nextSeq() {
        return ++lastSeq;
    }
}
