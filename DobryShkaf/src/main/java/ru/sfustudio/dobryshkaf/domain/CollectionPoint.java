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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.sfustudio.dobryshkaf.exception.ConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Пункт обмена или приёма вещей на карте. */
@Entity
@Table(name = "collection_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionPoint {

    private static final int COORDINATE_SCALE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_type", nullable = false, length = 32)
    private PointType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, precision = 9, scale = COORDINATE_SCALE)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = COORDINATE_SCALE)
    private BigDecimal longitude;

    @Column(nullable = false, length = 500)
    private String hours;

    @Column(nullable = false, length = 500)
    private String contacts;

    @ManyToMany
    @JoinTable(
            name = "collection_point_categories",
            joinColumns = @JoinColumn(name = "point_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    /** Что и в каком виде пункт принимает. */
    @Column(nullable = false, length = 2000)
    private String conditions;

    /** Как получить вещи; есть только у пунктов обмена. */
    @Column(length = 2000)
    private String receivingConditions;

    /** Откуда взяты данные: сайт организации, звонок и т. п. */
    @Column(nullable = false, length = 300)
    private String source;

    @Column(nullable = false)
    private LocalDate verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PointStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public CollectionPoint(PointDetails details) {
        this.status = PointStatus.ACTIVE;
        apply(details);
    }

    public void update(PointDetails details) {
        requireNotArchived();
        apply(details);
    }

    /** Открыть пункт или отметить временное закрытие; архивирование — отдельная операция. */
    public void changeStatus(PointStatus newStatus) {
        requireNotArchived();
        if (newStatus == PointStatus.ARCHIVED) {
            throw new IllegalArgumentException("Use archive() to archive a point");
        }
        this.status = Objects.requireNonNull(newStatus, "status must not be null");
    }

    public void archive() {
        this.status = PointStatus.ARCHIVED;
    }

    public boolean isVisible() {
        return status != PointStatus.ARCHIVED;
    }

    public boolean isTemporarilyClosed() {
        return status == PointStatus.TEMPORARILY_CLOSED;
    }

    private void apply(PointDetails details) {
        this.type = Objects.requireNonNull(details.type(), "type must not be null");
        this.name = details.name();
        this.organization = Objects.requireNonNull(details.organization(), "organization must not be null");
        this.district = Objects.requireNonNull(details.district(), "district must not be null");
        this.address = details.address();
        this.latitude = details.latitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        this.longitude = details.longitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        this.hours = details.hours();
        this.contacts = details.contacts();
        this.categories.clear();
        this.categories.addAll(details.categories());
        this.conditions = details.conditions();
        // В пункте приёма вещи не выдают, поэтому условия получения к нему не относятся
        this.receivingConditions = type == PointType.EXCHANGE ? details.receivingConditions() : null;
        this.source = details.source();
        this.verifiedAt = Objects.requireNonNull(details.verifiedAt(), "verifiedAt must not be null");
    }

    private void requireNotArchived() {
        if (status == PointStatus.ARCHIVED) {
            throw new ConflictException("POINT_ARCHIVED", "Архивный пункт нельзя изменить");
        }
    }
}
