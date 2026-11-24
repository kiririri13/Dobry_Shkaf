package ru.sfustudio.dobryshkaf.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemStatus;
import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.CollectionPointRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.TransferRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EcoService {

    private static final String NO_ESTIMATE = "Оценка CO₂ для этой категории пока недоступна";
    private static final String CREDIT_ORDER = " order by category, line_id";

    private final JdbcClient jdbc;
    private final ItemRepository items;
    private final TransferRepository transfers;
    private final CollectionPointRepository points;

    @Transactional
    public List<Line> replaceLines(UUID itemId, List<LineInput> inputs, CurrentUser owner) {
        Item item = ownerItem(itemId, owner, true);
        if ((item.getStatus() != ItemStatus.DRAFT && item.getStatus() != ItemStatus.PUBLISHED) || event(itemId) != null) {
            throw new ConflictException("ECO_LINES_FROZEN", "Состав можно изменить до первого закрытия или передачи");
        }
        jdbc.sql("delete from eco_item_lines where item_id = ?").param(itemId).update();
        for (LineInput input : inputs) {
            if (input.previousLineId() != null) {
                if (input.category() != null || input.quantity() != null || input.weightKg() != null
                        || input.weightMode() != null) {
                    throw invalidLine("Для повторной передачи укажите только исходную позицию");
                }
                int added = jdbc.sql("""
                        insert into eco_item_lines (item_id, origin_line_id, category, quantity, weight_kg)
                        select :item, l.origin_line_id, l.category, l.quantity, l.weight_kg
                        from eco_credits l join eco_events e on e.item_id = l.item_id
                        where l.line_id = :previous and e.active and (e.author_id = :owner or exists (
                            select 1 from transfers t where t.item_id = l.item_id and t.receiver_id = :owner
                                and t.status = 'COMPLETED'))
                        on conflict do nothing
                        """).param("item", itemId).param("previous", input.previousLineId())
                        .param("owner", owner.id()).update();
                if (added == 0) {
                    throw invalidLine("Укажите доступную исходную позицию один раз");
                }
            } else {
                if (input.category() == null || input.quantity() == null
                        || (input.weightKg() == null && input.weightMode() != null)
                        || (input.weightKg() != null && input.weightMode() == null)) {
                    throw invalidLine("Укажите категорию, количество и способ взвешивания при указании массы");
                }
                BigDecimal totalWeight = input.weightMode() == WeightMode.PER_UNIT
                        ? input.weightKg().multiply(BigDecimal.valueOf(input.quantity())) : input.weightKg();
                jdbc.sql("insert into eco_item_lines (item_id, category, quantity, weight_kg) values (?, ?, ?, ?)")
                        .params(itemId, input.category(), input.quantity(), totalWeight).update();
            }
        }
        return lines(itemId);
    }

    @Transactional(readOnly = true)
    public ItemEco details(UUID itemId, CurrentUser owner) {
        ownerItem(itemId, owner, false);
        return new ItemEco(lines(itemId), event(itemId), jdbc.sql("""
                select line_id, origin_line_id, category, quantity, weight_kg, coefficient, source,
                       coefficient_version, round(co2e_kg, 1) as co2e_kg, counted
                from eco_credits where item_id = ?
                """ + CREDIT_ORDER).param(itemId).query(Credit.class).list(), recalculations(itemId));
    }

    @Transactional
    public List<Recalculation> recalculate(UUID itemId, String reason, CurrentUser actor) {
        items.findForUpdateById(itemId).orElseThrow(() -> new NotFoundException("Объявление не найдено"));
        if (event(itemId) == null) {
            throw new ConflictException("ECO_EVENT_MISSING", "У объявления ещё нет экологического расчёта");
        }
        jdbc.sql("""
                insert into eco_recalculations (line_id, coefficient_version, coefficient, source, co2e_kg, actor_id, reason)
                select c.line_id, k.version, k.coefficient, k.source, c.quantity * k.coefficient, :actor, :reason
                from eco_credits c join lateral (
                    select version, coefficient, source from eco_coefficients
                    where category = c.category order by version desc limit 1
                ) k on true
                where c.item_id = :item and k.version > coalesce(c.coefficient_version, 0)
                on conflict do nothing
                """).param("item", itemId).param("actor", actor.id()).param("reason", reason).update();
        return recalculations(itemId);
    }

    private List<Recalculation> recalculations(UUID itemId) {
        return jdbc.sql("""
                select r.line_id, r.coefficient_version, r.coefficient, r.source, round(r.co2e_kg, 1) as co2e_kg,
                       r.reason, r.created_at
                from eco_recalculations r join eco_credits c on c.line_id = r.line_id
                where c.item_id = ? order by r.created_at, r.line_id, r.coefficient_version
                """).param(itemId).query(Recalculation.class).list();
    }

    @Transactional(readOnly = true)
    public List<Line> transferLines(UUID transferId, CurrentUser viewer) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> new NotFoundException("Передача не найдена"));
        if (transfer.roleOf(viewer.id()).isEmpty()) {
            throw new ForbiddenException("Вы не участник этой передачи");
        }
        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new ConflictException("TRANSFER_NOT_COMPLETED", "Состав доступен после завершения передачи");
        }
        return lines(transfer.getItem().getId());
    }

    // ItemService и TransferService уже держат блокировку строки объявления.
    void credit(Item item) {
        UUID itemId = item.getId();
        if (event(itemId) != null) {
            lockOrigins(itemId);
            jdbc.sql("update eco_events set active = true, credited_at = now() where item_id = ?")
                    .param(itemId).update();
            jdbc.sql("""
                    update eco_credits c set counted = not exists (
                        select 1 from eco_credits other where other.origin_line_id = c.origin_line_id and other.counted)
                    where c.item_id = ? and not c.counted
                    """).param(itemId).update();
            return;
        }
        if (lines(itemId).isEmpty()) {
            jdbc.sql("insert into eco_item_lines (item_id, category, quantity) values (?, 'UNSPECIFIED', 1)")
                    .param(itemId).update();
        }
        lockOrigins(itemId);
        jdbc.sql("insert into eco_events (item_id, author_id) values (?, ?)")
                .params(itemId, item.getOwner().getId()).update();
        jdbc.sql("""
                insert into eco_credits (line_id, item_id, origin_line_id, category, quantity, weight_kg,
                                         coefficient, source, coefficient_version, co2e_kg, counted)
                select l.id, l.item_id, coalesce(l.origin_line_id, l.id), l.category, l.quantity, l.weight_kg,
                       k.coefficient, k.source, k.version, l.quantity * k.coefficient,
                       not exists (select 1 from eco_credits c
                                   where c.origin_line_id = coalesce(l.origin_line_id, l.id) and c.counted)
                from eco_item_lines l left join lateral (
                    select coefficient, source, version from eco_coefficients k
                    where k.category = l.category order by version desc limit 1
                ) k on true
                where l.item_id = ?
                """).param(itemId).update();
    }

    void deactivate(UUID itemId) {
        lockOrigins(itemId);
        jdbc.sql("update eco_events set active = false where item_id = ?").param(itemId).update();
        jdbc.sql("update eco_credits set counted = false where item_id = ?").param(itemId).update();
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID author) {
        long completed = author == null ? transfers.countByStatus(TransferStatus.COMPLETED)
                : transfers.countForParticipant(author, TransferStatus.COMPLETED);
        long closed = jdbc.sql("""
                select count(*) from items i where i.status = 'CLOSED' and i.published_at is not null
                    and (cast(:author as uuid) is null or i.owner_id = cast(:author as uuid))
                """).param("author", author).query(Long.class).single();
        Totals totals = jdbc.sql("""
                select coalesce(sum(c.quantity), 0) as counted_items,
                    coalesce(sum(c.quantity) filter (where coalesce(r.co2e_kg, c.co2e_kg) is not null), 0) as estimated_items,
                    round(sum(coalesce(r.co2e_kg, c.co2e_kg)), 1) as co2e_kg, sum(c.weight_kg) as weight_kg
                from eco_credits c join eco_events e on e.item_id = c.item_id
                left join lateral (
                    select co2e_kg from eco_recalculations r where r.line_id = c.line_id
                    order by coefficient_version desc limit 1
                ) r on true
                where e.active and c.counted and (cast(:author as uuid) is null or e.author_id = cast(:author as uuid))
                """).param("author", author).query(Totals.class).single();
        List<PointTotals> pointTotals = author == null ? jdbc.sql("""
                select kind, count(*) as batches, sum(quantity) as quantity, sum(weight_kg) as weight_kg
                from eco_point_batches group by kind order by kind
                """).query(PointTotals.class).list() : List.of();
        return new Stats(completed, closed, totals, byCategory(author), pointTotals, "/api/eco/methodology",
                totals.countedItems() > totals.estimatedItems() ? NO_ESTIMATE : null);
    }

    private List<CategoryContribution> byCategory(UUID author) {
        return jdbc.sql("""
                with counted as (
                    select i.category_id, c.quantity
                    from eco_credits c join eco_events e on e.item_id = c.item_id
                    join items i on i.id = e.item_id
                    where e.active and c.counted
                        and (cast(:author as uuid) is null or e.author_id = cast(:author as uuid))
                    union all
                    select i.category_id, 1
                    from items i
                    where i.status in ('CLOSED', 'TRANSFERRED') and i.published_at is not null
                        and (cast(:author as uuid) is null or i.owner_id = cast(:author as uuid))
                        and not exists (select 1 from eco_events e where e.item_id = i.id)
                )
                select c.id as category_id, coalesce(c.code, 'uncategorized') as code,
                       coalesce(c.name, 'Без категории') as name, sum(counted.quantity) as quantity
                from counted left join categories c on c.id = counted.category_id
                group by c.id, c.code, c.name, c.sort_order order by c.sort_order nulls last, c.code
                """).param("author", author).query(CategoryContribution.class).list();
    }

    public Methodology methodology() {
        List<Coefficient> coefficients = jdbc.sql("""
                select distinct on (category) category, version, coefficient, source
                from eco_coefficients order by category, version desc
                """).query(Coefficient.class).list();
        return new Methodology("Оценочный углеродный эквивалент", "кг CO₂-экв.",
                "Оценка соответствует производству и распределению нового аналога, если передача замещает его покупку.",
                NO_ESTIMATE, coefficients);
    }

    @Transactional
    public PointBatch recordBatch(UUID id, UUID pointId, BatchKind kind, int quantity, BigDecimal weightKg,
                                  CurrentUser actor) {
        if (!points.existsById(pointId)) {
            throw new NotFoundException("Пункт не найден");
        }
        return jdbc.sql("""
                insert into eco_point_batches (id, point_id, author_id, kind, quantity, weight_kg) values (?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing returning id, point_id, kind, quantity, weight_kg, created_at
                """).params(id, pointId, actor.id(), kind.name(), quantity, weightKg).query(PointBatch.class).optional()
                .orElseGet(() -> {
                    PointBatch existing = jdbc.sql("""
                            select id, point_id, kind, quantity, weight_kg, created_at from eco_point_batches where id = ?
                            """).param(id).query(PointBatch.class).single();
                    if (!existing.pointId().equals(pointId) || existing.kind() != kind || existing.quantity() != quantity
                            || existing.weightKg().compareTo(weightKg) != 0) {
                        throw new ConflictException("ECO_BATCH_EXISTS", "Эта партия уже учтена с другими данными");
                    }
                    return existing;
                });
    }

    private List<Line> lines(UUID itemId) {
        return jdbc.sql("""
                select id, origin_line_id, category, quantity, weight_kg
                from eco_item_lines where item_id = ? order by category, id
                """).param(itemId).query(Line.class).list();
    }

    private EcoEvent event(UUID itemId) {
        return jdbc.sql("select active, credited_at from eco_events where item_id = ?")
                .param(itemId).query(EcoEvent.class).optional().orElse(null);
    }

    private void lockOrigins(UUID itemId) {
        jdbc.sql("""
                select id from eco_item_lines where id in (
                    select coalesce(origin_line_id, id) from eco_item_lines where item_id = ?)
                order by id for no key update
                """).param(itemId).query(UUID.class).list();
    }

    private Item ownerItem(UUID id, CurrentUser owner, boolean lock) {
        Item item = (lock ? items.findForUpdateById(id) : items.findById(id))
                .orElseThrow(() -> new NotFoundException("Объявление не найдено"));
        if (!item.isOwnedBy(owner.id())) {
            throw new ForbiddenException("Данные объявления доступны его владельцу");
        }
        return item;
    }

    private static FieldValidationException invalidLine(String message) {
        return new FieldValidationException("VALIDATION_FAILED", "Проверьте состав", Map.of("lines", message));
    }

    public enum WeightMode { BATCH, PER_UNIT }
    public enum BatchKind { CONTAINER_COLLECTION, CONFIRMED_DISTRIBUTION }

    public record LineInput(@Pattern(regexp = "[A-Z0-9_]{1,50}") String category,
                            @Min(1) @Max(1_000_000) Integer quantity,
                            @DecimalMin("0.001") @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
                            WeightMode weightMode, UUID previousLineId) {
    }

    public record Line(UUID id, UUID originLineId, String category, int quantity, BigDecimal weightKg) {
    }

    public record Credit(UUID lineId, UUID originLineId, String category, int quantity, BigDecimal weightKg,
                         BigDecimal coefficient, String source, Integer coefficientVersion, BigDecimal co2eKg,
                         boolean counted) {
        public String getEstimateNotice() {
            return coefficient == null ? NO_ESTIMATE : null;
        }
    }

    public record EcoEvent(boolean active, OffsetDateTime creditedAt) {
    }

    public record ItemEco(List<Line> lines, EcoEvent event, List<Credit> credits, List<Recalculation> recalculations) {
    }

    public record Recalculation(UUID lineId, int coefficientVersion, BigDecimal coefficient, String source,
                                BigDecimal co2eKg, String reason, OffsetDateTime createdAt) {
    }

    public record Totals(long countedItems, long estimatedItems, BigDecimal co2eKg, BigDecimal weightKg) {
    }

    public record Stats(long completedTransfers, long closedListings, Totals contribution,
                        List<CategoryContribution> byCategory, List<PointTotals> pointBatches,
                        String methodologyUrl, String estimateNotice) {
    }

    public record CategoryContribution(UUID categoryId, String code, String name, long quantity) {
    }

    public record Coefficient(String category, int version, BigDecimal coefficient, String source) {
    }

    public record Methodology(String name, String unit, String explanation, String noEstimate, List<Coefficient> coefficients) {
    }

    public record PointBatch(UUID id, UUID pointId, BatchKind kind, int quantity, BigDecimal weightKg, OffsetDateTime createdAt) {
    }

    public record PointTotals(BatchKind kind, long batches, long quantity, BigDecimal weightKg) {
    }
}
