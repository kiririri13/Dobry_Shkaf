package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.sfustudio.dobryshkaf.domain.DeliveryMethod;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.TransferRole;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final MediaFiles mediaFiles;

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

    @Transactional
    public PublicProfile setAvatar(CurrentUser viewer, MultipartFile file) {
        User user = accounts.lockActive(viewer);
        byte[] bytes = mediaFiles.imageBytes(file);
        ImageFormat format = mediaFiles.imageFormat(bytes);
        String key = "avatars/%s/%s.%s".formatted(user.getId(), UUID.randomUUID(), format.getExtension());
        String previous = user.getAvatarKey();
        mediaFiles.put(key, bytes, format.getContentType());
        user.changeAvatar(key, format.getContentType());
        mediaFiles.deleteAfterCommit(previous);
        return publicProfile(user);
    }

    @Transactional
    public void removeAvatar(CurrentUser viewer) {
        User user = accounts.lockActive(viewer);
        mediaFiles.deleteAfterCommit(user.getAvatarKey());
        user.changeAvatar(null, null);
    }

    @Transactional
    public StoredPhoto avatar(UUID id) {
        // Блокировка на чтение не даёт заменить и удалить файл до окончания запроса.
        User user = users.findForShareById(id).orElseThrow(() -> new NotFoundException("Профиль не найден"));
        if (user.getAvatarKey() == null) {
            throw new NotFoundException("Аватар не найден");
        }
        return new StoredPhoto(mediaFiles.get(user.getAvatarKey()), user.getAvatarContentType());
    }

    public PublicHistory history(UUID id, int page) {
        if (!users.existsById(id)) {
            throw new NotFoundException("Профиль не найден");
        }
        int pageSize = 20;
        List<HistoryEntry> entries = jdbc.sql("""
                select t.id, i.id as item_id, i.title, i.delivery_method, t.completed_at,
                       case when t.giver_id = :user then 'GIVER' else 'RECEIVER' end as role
                from transfers t join items i on i.id = t.item_id
                where t.status = 'COMPLETED' and i.moderation_status <> 'HIDDEN'
                    and (t.giver_id = :user or t.receiver_id = :user)
                order by t.completed_at desc, t.id desc limit :limit offset :offset
                """).param("user", id).param("limit", pageSize + 1).param("offset", (long) pageSize * page)
                .query(HistoryEntry.class).list();
        boolean hasMore = entries.size() > pageSize;
        return new PublicHistory(hasMore ? entries.subList(0, pageSize) : entries, page, hasMore);
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
        String avatarUrl = user.getAvatarKey() == null ? null : "/api/profiles/" + user.getId() + "/avatar";
        return new PublicProfile(user.getId(), user.getName(),
                user.getDistrict() == null ? null : DistrictResponse.from(user.getDistrict()),
                user.getRating(), transfers.countCompleted(user.getId()), user.getCreatedAt(),
                user.getCreatedAt().atOffset(ZoneOffset.UTC).getYear(), avatarUrl);
    }

    public record PublicProfile(UUID id, String name, DistrictResponse district, BigDecimal rating,
                                long completedTransfers, Instant registeredAt, int memberSinceYear, String avatarUrl) {
    }

    public record HistoryEntry(UUID id, UUID itemId, String title, DeliveryMethod deliveryMethod,
                               OffsetDateTime completedAt, TransferRole role) {
    }

    public record PublicHistory(List<HistoryEntry> items, int page, boolean hasMore) {
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
