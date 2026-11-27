package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.District;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemPhoto;
import ru.sfustudio.dobryshkaf.domain.ItemStatus;
import ru.sfustudio.dobryshkaf.dto.ItemFilter;
import ru.sfustudio.dobryshkaf.dto.ItemRequest;
import ru.sfustudio.dobryshkaf.dto.ItemResponse;
import ru.sfustudio.dobryshkaf.dto.PageResponse;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.InvalidPhotoException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.ItemPhotoRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemService {

    public static final int PAGE_SIZE = 20;

    private static final Map<String, String> PUBLICATION_MESSAGES = Map.of(
            "description", "Добавьте описание",
            "condition", "Укажите состояние",
            "categoryId", "Выберите категорию",
            "size", "Укажите размер",
            "districtId", "Укажите район",
            "photos", "Добавьте хотя бы одно фото");

    private final ItemRepository itemRepository;
    private final ItemPhotoRepository itemPhotoRepository;
    private final CategoryRepository categoryRepository;
    private final DistrictRepository districtRepository;
    private final MediaFiles mediaFiles;
    private final TransferService transferService;
    private final ActiveAccounts accounts;
    private final EcoService eco;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(ItemFilter filter, Optional<CurrentUser> viewer) {
        Sort sort = filter.onlyMine()
                ? Sort.by(Sort.Direction.DESC, "createdAt", "id")
                : Sort.by(Sort.Direction.DESC, "publishedAt", "id");
        PageRequest pageRequest = PageRequest.of(filter.pageOrDefault(), PAGE_SIZE, sort);
        return PageResponse.from(itemRepository.findAll(ItemSpecifications.matching(filter, viewer), pageRequest)
                .map(ItemResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> publicByOwner(UUID id, int page) {
        PageRequest request = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
        return PageResponse.from(itemRepository.findAll(ItemSpecifications.publicByOwner(id), request).map(ItemResponse::from));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(UUID id, Optional<CurrentUser> viewer) {
        return ItemResponse.from(findVisible(id, viewer));
    }

    @Transactional
    public ItemResponse create(ItemRequest request, CurrentUser user) {
        Item item = new Item(accounts.lockActive(user), request.title());
        apply(item, request);
        return respond(itemRepository.save(item));
    }

    @Transactional
    public ItemResponse update(UUID id, ItemRequest request, CurrentUser user) {
        Item item = findManageable(id, user);
        apply(item, request);
        requireCompleteIfPublished(item);
        return respond(item);
    }

    @Transactional
    public ItemResponse publish(UUID id, CurrentUser user) {
        accounts.lockActive(user);
        Item item = findManageable(id, user);
        if (item.getStatus() == ItemStatus.DRAFT) {
            requireComplete(item);
        }
        item.publish(Instant.now());
        notifications.item(item, NotificationService.Type.ITEM_SUBMITTED, null);
        return respond(item);
    }

    @Transactional
    public ItemResponse close(UUID id, CurrentUser user) {
        Item item = findManageable(id, user);
        if (item.getStatus() == ItemStatus.CLOSED) {
            return ItemResponse.from(item);
        }
        boolean published = item.getStatus() == ItemStatus.PUBLISHED && item.isPublic();
        item.close();
        // Закрытую вещь уже не получить: ждущие ответа запросы отклоняются, история сохраняется
        transferService.rejectOpenRequests(item, user.id());
        if (published) {
            eco.credit(item);
            notifications.item(item, NotificationService.Type.ITEM_UNPUBLISHED, null);
        }
        return respond(item);
    }

    @Transactional
    public ItemResponse reopen(UUID id, CurrentUser user) {
        Item item = findManageable(id, user);
        if (item.getStatus() == ItemStatus.PUBLISHED) {
            return ItemResponse.from(item);
        }
        requireComplete(item);
        item.reopen(Instant.now());
        eco.deactivate(id);
        if (item.isPublic()) {
            notifications.item(item, NotificationService.Type.ITEM_PUBLISHED, null);
        }
        return respond(item);
    }

    @Transactional
    public ItemResponse addPhoto(UUID id, MultipartFile file, CurrentUser user) {
        Item item = itemRepository.findForUpdateById(id)
                .filter(found -> canView(found, Optional.of(user)))
                .orElseThrow(ItemService::itemNotFound);
        requireCanManage(item, user);
        if (!item.hasRoomForPhoto()) {
            throw new InvalidPhotoException("PHOTO_LIMIT_REACHED",
                    "Можно добавить не больше %d фото".formatted(Item.MAX_PHOTOS));
        }
        byte[] content = mediaFiles.imageBytes(file);
        ImageFormat format = mediaFiles.imageFormat(content);

        String key = "items/%s/%s.%s".formatted(item.getId(), UUID.randomUUID(), format.getExtension());
        item.addPhoto(key, format.getContentType(), content.length);
        mediaFiles.put(key, content, format.getContentType());
        return respond(item);
    }

    @Transactional
    public ItemResponse deletePhoto(UUID id, UUID photoId, CurrentUser user) {
        Item item = itemRepository.findForUpdateById(id)
                .filter(found -> canView(found, Optional.of(user)))
                .orElseThrow(ItemService::itemNotFound);
        requireCanManage(item, user);
        ItemPhoto photo = item.getPhotos().stream()
                .filter(candidate -> candidate.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Фото не найдено"));

        item.removePhoto(photo);
        requireCompleteIfPublished(item);
        // Файл удаляем только после фиксации: при откате запись о фото останется и должна указывать на файл
        mediaFiles.deleteAfterCommit(photo.getStorageKey());
        return respond(item);
    }

    @Transactional(readOnly = true)
    public StoredPhoto getPhoto(UUID photoId, Optional<CurrentUser> viewer) {
        ItemPhoto photo = itemPhotoRepository.findWithItemById(photoId)
                .filter(found -> canView(found.getItem(), viewer) || found.getItem().hasPublicHistoryPhoto())
                .orElseThrow(() -> new NotFoundException("Фото не найдено"));
        return new StoredPhoto(mediaFiles.get(photo.getStorageKey()), photo.getContentType());
    }

    private Item findVisible(UUID id, Optional<CurrentUser> viewer) {
        return itemRepository.findById(id)
                .filter(item -> canView(item, viewer))
                .orElseThrow(ItemService::itemNotFound);
    }

    /** Чужой черновик для постороннего не существует (404), а чужое опубликованное объявление менять нельзя (403). */
    private Item findManageable(UUID id, CurrentUser user) {
        Item item = itemRepository.findForUpdateById(id)
                .filter(found -> canView(found, Optional.of(user)))
                .orElseThrow(ItemService::itemNotFound);
        requireCanManage(item, user);
        return item;
    }

    private static boolean canView(Item item, Optional<CurrentUser> viewer) {
        return item.isPublic() || viewer.map(user -> user.canManage(item)
                || (user.moderator() && item.isSubmitted())).orElse(false);
    }

    private static void requireCanManage(Item item, CurrentUser user) {
        if (!user.canManage(item)) {
            throw new ForbiddenException("Изменять объявление может только его владелец");
        }
    }

    private void apply(Item item, ItemRequest request) {
        item.edit(
                request.title().trim(),
                trimToNull(request.description()),
                request.condition(),
                trimToNull(request.size()),
                category(request.categoryId()),
                district(request.districtId()));
        if (request.deliveryMethod() != null) {
            item.changeDeliveryMethod(request.deliveryMethod());
        }
    }

    private Category category(UUID id) {
        if (id == null) {
            return null;
        }
        return categoryRepository.findById(id).orElseThrow(() -> invalidReference("categoryId", "Категория не найдена"));
    }

    private District district(UUID id) {
        if (id == null) {
            return null;
        }
        return districtRepository.findById(id).orElseThrow(() -> invalidReference("districtId", "Район не найден"));
    }

    /** Опубликованное объявление не должно потерять обязательные поля при редактировании. */
    private static void requireCompleteIfPublished(Item item) {
        if (item.getStatus() == ItemStatus.PUBLISHED) {
            requireComplete(item);
        }
    }

    private static void requireComplete(Item item) {
        Set<String> missing = item.missingForPublication();
        if (!missing.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            missing.forEach(field -> fields.put(field, PUBLICATION_MESSAGES.get(field)));
            throw new FieldValidationException("ITEM_INCOMPLETE", "Заполните все поля, нужные для публикации", fields);
        }
    }

    public static InvalidPhotoException photoTooLarge() {
        return new InvalidPhotoException("PHOTO_TOO_LARGE", "Фото должно быть не больше 5 МБ");
    }

    private ItemResponse respond(Item item) {
        // Время создания и изменения Hibernate проставляет при записи в БД
        itemRepository.flush();
        return ItemResponse.from(item);
    }

    private static NotFoundException itemNotFound() {
        return new NotFoundException("Объявление не найдено");
    }

    private static FieldValidationException invalidReference(String field, String message) {
        return new FieldValidationException("VALIDATION_FAILED", "Проверьте заполнение полей", Map.of(field, message));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
