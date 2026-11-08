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
import java.util.UUID;

/** Метаданные фото; сам файл лежит в объектном хранилище под ключом {@link #storageKey}. */
@Entity
@Table(name = "item_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @Column(nullable = false, length = 200, updatable = false)
    private String storageKey;

    @Column(nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    ItemPhoto(Item item, String storageKey, String contentType, int sizeBytes, int sortOrder) {
        this.item = item;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sortOrder = sortOrder;
    }
}
