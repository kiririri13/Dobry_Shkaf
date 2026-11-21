package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.PointStatus;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.dto.DistrictResponse;
import ru.sfustudio.dobryshkaf.dto.ItemResponse;
import ru.sfustudio.dobryshkaf.dto.PointResponse;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.CollectionPointRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository users;
    private final DistrictRepository districts;
    private final ItemRepository items;
    private final CollectionPointRepository points;
    private final TransferService transfers;
    private final JdbcClient jdbc;
    private final ActiveAccounts accounts;

    public PrivateProfile me(CurrentUser viewer) {
        User user = findUser(viewer.id());
        return new PrivateProfile(publicProfile(user), user.getEmail(), user.getPhone());
    }

    public PublicProfile profile(UUID id) {
        return publicProfile(findUser(id));
    }

    @Transactional
    public PrivateProfile update(CurrentUser viewer, String name, UUID districtId) {
        User user = accounts.lockActive(viewer);
        user.rename(name.strip());
        user.changeDistrict(districtId == null ? null : districts.findById(districtId)
                .orElseThrow(() -> new FieldValidationException("VALIDATION_FAILED", "Проверьте район",
                        Map.of("districtId", "Район не найден"))));
        return new PrivateProfile(publicProfile(user), user.getEmail(), user.getPhone());
    }

    public Favorites favorites(CurrentUser viewer) {
        List<ItemResponse> favoriteItems = jdbc.sql("""
                select f.item_id from favorite_items f join items i on i.id = f.item_id
                where f.user_id = ? and i.status in ('PUBLISHED', 'RESERVED') and i.moderation_status <> 'HIDDEN'
                order by f.created_at desc, f.item_id
                """).param(viewer.id()).query(UUID.class).list().stream()
                .map(id -> items.findById(id).filter(Item::isPublic))
                .flatMap(java.util.Optional::stream).map(ItemResponse::from).toList();
        List<PointResponse> favoritePoints = jdbc.sql("""
                select f.point_id from favorite_points f join collection_points p on p.id = f.point_id
                where f.user_id = ? and p.status <> 'ARCHIVED' order by f.created_at desc, f.point_id
                """).param(viewer.id()).query(UUID.class).list().stream()
                .map(points::findById).flatMap(java.util.Optional::stream)
                .filter(point -> point.getStatus() != PointStatus.ARCHIVED).map(PointResponse::from).toList();
        return new Favorites(favoriteItems, favoritePoints);
    }

    @Transactional
    public void addFavorite(FavoriteType type, UUID id, CurrentUser viewer) {
        accounts.lockActive(viewer);
        boolean visible = switch (type) {
            case ITEMS -> items.findById(id).filter(Item::isPublic).isPresent();
            case POINTS -> points.findById(id).filter(point -> point.getStatus() != PointStatus.ARCHIVED).isPresent();
        };
        if (!visible) {
            throw new NotFoundException("Объект недоступен");
        }
        jdbc.sql("insert into %s (user_id, %s) values (?, ?) on conflict do nothing"
                .formatted(type.table, type.column)).params(viewer.id(), id).update();
    }

    @Transactional
    public void removeFavorite(FavoriteType type, UUID id, CurrentUser viewer) {
        jdbc.sql("delete from %s where user_id = ? and %s = ?"
                .formatted(type.table, type.column)).params(viewer.id(), id).update();
    }

    private User findUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Профиль не найден"));
    }

    private PublicProfile publicProfile(User user) {
        return new PublicProfile(user.getId(), user.getName(),
                user.getDistrict() == null ? null : DistrictResponse.from(user.getDistrict()),
                user.getRating(), transfers.countCompleted(user.getId()));
    }

    public record PublicProfile(UUID id, String name, DistrictResponse district, BigDecimal rating,
                                long completedTransfers) {
    }

    public record PrivateProfile(PublicProfile profile, String email, String phone) {
    }

    public record Favorites(List<ItemResponse> items, List<PointResponse> points) {
    }

    public enum FavoriteType {
        ITEMS("favorite_items", "item_id"),
        POINTS("favorite_points", "point_id");

        private final String table;
        private final String column;

        FavoriteType(String table, String column) {
            this.table = table;
            this.column = column;
        }
    }
}
