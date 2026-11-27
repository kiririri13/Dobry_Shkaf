package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemModerationStatus;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.dto.ItemResponse;
import ru.sfustudio.dobryshkaf.dto.PageResponse;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemModerationService {

    private final ItemRepository items;
    private final UserRepository users;
    private final TransferService transfers;
    private final ModerationLog log;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> queue(ItemModerationStatus status, int page) {
        Specification<Item> spec = (root, query, cb) -> root.get("status").in(Item.SUBMITTED_STATUSES);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("moderationStatus"), status));
        }
        return PageResponse.from(items.findAll(spec,
                PageRequest.of(page, ItemService.PAGE_SIZE, Sort.by(Sort.Direction.DESC, "publishedAt", "id")))
                .map(ItemResponse::from));
    }

    @Transactional
    public ItemResponse change(UUID id, ItemAction action, String reason, CurrentUser actor) {
        Item item = items.findForUpdateById(id).orElseThrow(() -> new NotFoundException("Объявление не найдено"));
        if (action == ItemAction.REVIEW && item.getModerationStatus() == ItemModerationStatus.HIDDEN) {
            throw new ConflictException("ITEM_HIDDEN", "Сначала восстановите объявление");
        }
        if (action == ItemAction.RESTORE && item.getModerationStatus() != ItemModerationStatus.HIDDEN) {
            return ItemResponse.from(item);
        }
        ItemModerationStatus status = switch (action) {
            case REVIEW -> ItemModerationStatus.REVIEWED;
            case HIDE -> ItemModerationStatus.HIDDEN;
            case RESTORE -> ItemModerationStatus.PENDING;
        };
        if (item.moderate(status)) {
            if (action == ItemAction.HIDE) {
                transfers.rejectOpenRequests(item, actor.id());
            }
            log.record(actor, "ITEM", id, action.name(), reason, null);
            NotificationService.Type notification = switch (action) {
                case REVIEW -> NotificationService.Type.ITEM_REVIEWED;
                case HIDE -> NotificationService.Type.ITEM_HIDDEN;
                case RESTORE -> NotificationService.Type.ITEM_RESTORED;
            };
            notifications.item(item, notification, reason);
        }
        items.flush();
        return ItemResponse.from(item);
    }

    @Transactional
    public void block(UUID id, String reason, CurrentUser actor) {
        User user = users.findForUpdateById(id).orElseThrow(() -> new NotFoundException("Профиль не найден"));
        if (actor.id().equals(id)) {
            throw new ConflictException("SELF_BLOCK", "Свой аккаунт заблокировать нельзя");
        }
        if (!user.isBlocked()) {
            user.block();
            log.record(actor, "PROFILE", id, "BLOCK_USER", reason, null);
            notifications.create(id, NotificationService.Type.ACCOUNT_BLOCKED, id, reason);
        }
    }

    public enum ItemAction {
        REVIEW, HIDE, RESTORE
    }
}
