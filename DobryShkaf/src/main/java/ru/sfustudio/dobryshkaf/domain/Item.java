package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.sfustudio.dobryshkaf.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    public static final int MAX_PHOTOS = 5;

    /** Статусы, в которых объявление видно всем, а не только владельцу. */
    public static final Set<ItemStatus> PUBLIC_STATUSES = EnumSet.of(ItemStatus.PUBLISHED, ItemStatus.RESERVED);

    private static final Set<ItemStatus> EDITABLE_STATUSES = EnumSet.of(ItemStatus.DRAFT, ItemStatus.PUBLISHED);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", length = 32)
    private ItemCondition condition;

    @Column(length = 20)
    private String size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryMethod deliveryMethod = DeliveryMethod.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemModerationStatus moderationStatus = ItemModerationStatus.PENDING;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ItemPhoto> photos = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Item(User owner, String title) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.title = requireTitle(title);
        this.status = ItemStatus.DRAFT;
    }

    public void edit(String title, String description, ItemCondition condition, String size,
                     Category category, District district) {
        requireEditable();
        this.title = requireTitle(title);
        this.description = description;
        this.condition = condition;
        this.size = size;
        this.category = category;
        this.district = district;
        markPendingIfVisible();
    }

    /** Поля, без которых объявление нельзя опубликовать; пустой набор — можно. */
    public Set<String> missingForPublication() {
        Set<String> missing = new LinkedHashSet<>();
        if (description == null || description.isBlank()) {
            missing.add("description");
        }
        if (condition == null) {
            missing.add("condition");
        }
        if (category == null) {
            missing.add("categoryId");
        } else if (category.isSizeRequired() && (size == null || size.isBlank())) {
            missing.add("size");
        }
        if (district == null) {
            missing.add("districtId");
        }
        if (photos.isEmpty()) {
            missing.add("photos");
        }
        return missing;
    }

    public void publish(Instant now) {
        if (status != ItemStatus.DRAFT) {
            throw new ConflictException("ITEM_NOT_DRAFT", "Опубликовать можно только черновик");
        }
        status = ItemStatus.PUBLISHED;
        publishedAt = now;
    }

    public void close() {
        requireEditable();
        status = ItemStatus.CLOSED;
    }

    public void changeDeliveryMethod(DeliveryMethod deliveryMethod) {
        requireEditable();
        this.deliveryMethod = Objects.requireNonNull(deliveryMethod);
    }

    public void reopen(Instant now) {
        if (status != ItemStatus.CLOSED || publishedAt == null) {
            throw new ConflictException("ITEM_NOT_REOPENABLE", "Открыть заново можно ранее опубликованное объявление");
        }
        status = ItemStatus.PUBLISHED;
        publishedAt = now;
    }

    public boolean moderate(ItemModerationStatus next) {
        if (!PUBLIC_STATUSES.contains(status)) {
            throw new ConflictException("ITEM_NOT_MODERATABLE", "Объявление сейчас вне очереди модерации");
        }
        if (moderationStatus == next) {
            return false;
        }
        moderationStatus = next;
        return true;
    }

    private void markPendingIfVisible() {
        if (moderationStatus == ItemModerationStatus.REVIEWED) {
            moderationStatus = ItemModerationStatus.PENDING;
        }
    }

    public ItemPhoto addPhoto(String storageKey, String contentType, int sizeBytes) {
        requireEditable();
        int nextOrder = photos.stream().mapToInt(ItemPhoto::getSortOrder).max().orElse(-1) + 1;
        ItemPhoto photo = new ItemPhoto(this, storageKey, contentType, sizeBytes, nextOrder);
        photos.add(photo);
        markPendingIfVisible();
        return photo;
    }

    public void removePhoto(ItemPhoto photo) {
        requireEditable();
        photos.remove(photo);
        markPendingIfVisible();
    }

    public boolean hasRoomForPhoto() {
        return photos.size() < MAX_PHOTOS;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner.getId().equals(userId);
    }

    public boolean isPublic() {
        return PUBLIC_STATUSES.contains(status) && moderationStatus != ItemModerationStatus.HIDDEN;
    }

    /** Вещь можно запросить: она опубликована и не в резерве. */
    public boolean isAvailable() {
        return status == ItemStatus.PUBLISHED && isPublic();
    }

    // Статусы резерва и передачи меняет только Transfer, чтобы вещь и передача не расходились

    void reserve() {
        if (!isAvailable()) {
            throw new ConflictException("ITEM_NOT_AVAILABLE", "Вещь сейчас недоступна для резерва");
        }
        status = ItemStatus.RESERVED;
    }

    void releaseReservation() {
        if (status == ItemStatus.RESERVED) {
            status = ItemStatus.PUBLISHED;
        }
    }

    void markTransferred() {
        status = ItemStatus.TRANSFERRED;
    }

    private void requireEditable() {
        if (!EDITABLE_STATUSES.contains(status)) {
            throw new ConflictException("ITEM_NOT_EDITABLE", "Объявление в этом статусе нельзя изменить");
        }
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        return title;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Item item && id != null && id.equals(item.getId());
    }

    @Override
    public int hashCode() {
        return Item.class.hashCode();
    }
}
