package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Chat;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.domain.TransferCommand;
import ru.sfustudio.dobryshkaf.domain.TransferEvent;
import ru.sfustudio.dobryshkaf.domain.TransferRole;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.dto.TransferEventResponse;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.TransferEventRepository;
import ru.sfustudio.dobryshkaf.repository.TransferRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Команды передачи. Каждая выполняется в своей транзакции под блокировкой строки вещи,
 * поэтому одновременные выбор получателя или подтверждения по одной вещи идут строго по очереди.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferEventRepository eventRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    /** Повтор запроса от того же получателя возвращает уже существующую активную передачу. */
    @Transactional
    public CommandResult<TransferResponse> request(UUID itemId, CurrentUser user) {
        Item item = itemRepository.findForUpdateById(itemId)
                .filter(Item::isPublic)
                .orElseThrow(() -> new NotFoundException("Объявление не найдено"));
        Optional<Transfer> active =
                transferRepository.findFirstByItemIdAndReceiverIdAndStatusIn(itemId, user.id(), Transfer.ACTIVE_STATUSES);
        if (active.isPresent()) {
            return new CommandResult<>(respond(active.get(), user), false);
        }
        if (item.isOwnedBy(user.id())) {
            throw new ConflictException("OWN_ITEM", "Нельзя запросить собственную вещь");
        }

        Chat chat = chatService.getOrCreateFor(item, user.id());
        Transfer transfer = transferRepository.save(new Transfer(item, userRepository.getReferenceById(user.id()), chat));
        record(transfer, user.id(), TransferCommand.REQUEST, null);
        return new CommandResult<>(respond(transfer, user), true);
    }

    @Transactional
    public TransferResponse select(UUID id, CurrentUser user) {
        return execute(id, user, TransferCommand.SELECT, (transfer, role) -> {
            if (role != TransferRole.GIVER) {
                throw new ForbiddenException("Выбрать получателя может только владелец вещи");
            }
            boolean changed = transfer.select();
            if (changed) {
                rejectRequests(transfer.getItem(), transfer, user.id());
            }
            return changed;
        });
    }

    @Transactional
    public TransferResponse confirm(UUID id, CurrentUser user) {
        return execute(id, user, TransferCommand.CONFIRM, (transfer, role) -> transfer.confirm(role, Instant.now()));
    }

    @Transactional
    public TransferResponse cancel(UUID id, CurrentUser user) {
        return execute(id, user, TransferCommand.CANCEL, Transfer::cancel);
    }

    @Transactional
    public TransferResponse acceptCancellation(UUID id, CurrentUser user) {
        return execute(id, user, TransferCommand.ACCEPT_CANCELLATION, Transfer::acceptCancellation);
    }

    @Transactional
    public TransferResponse declineCancellation(UUID id, CurrentUser user) {
        return execute(id, user, TransferCommand.DECLINE_CANCELLATION, Transfer::declineCancellation);
    }

    /** Вещь закрыта владельцем: ждущие ответа запросы отклоняются. */
    @Transactional
    public void rejectOpenRequests(Item item, UUID actorId) {
        rejectRequests(item, null, actorId);
    }

    /**
     * Владелец вещи видит все запросы на неё, остальные — только свои.
     * Без {@code itemId} — все передачи, где вы отдаёте или получаете.
     */
    @Transactional(readOnly = true)
    public List<TransferResponse> list(UUID itemId, TransferStatus status, CurrentUser user) {
        List<Transfer> transfers;
        if (itemId == null) {
            transfers = transferRepository.findAllForParticipant(user.id());
        } else {
            boolean owner = itemRepository.findById(itemId).map(item -> item.isOwnedBy(user.id())).orElse(false);
            transfers = owner
                    ? transferRepository.findByItemIdOrderByCreatedAtDesc(itemId)
                    : transferRepository.findByItemIdAndReceiverIdOrderByCreatedAtDesc(itemId, user.id());
        }
        return transfers.stream()
                .filter(transfer -> status == null || transfer.getStatus() == status)
                .map(transfer -> toResponse(transfer, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id, CurrentUser user) {
        Transfer transfer = transferRepository.findById(id).orElseThrow(TransferService::transferNotFound);
        requireRole(transfer, user);
        return toResponse(transfer, user);
    }

    @Transactional(readOnly = true)
    public List<TransferEventResponse> events(UUID id, CurrentUser user) {
        Transfer transfer = transferRepository.findById(id).orElseThrow(TransferService::transferNotFound);
        requireRole(transfer, user);
        return eventRepository.findByTransferIdOrderByCreatedAtAsc(id).stream().map(TransferEventResponse::from).toList();
    }

    /**
     * Сначала блокируется вещь, и только потом читается передача: прочитанная до блокировки
     * передача могла бы быть уже устаревшей.
     */
    private TransferResponse execute(UUID id, CurrentUser user, TransferCommand command,
                                     BiPredicate<Transfer, TransferRole> action) {
        UUID itemId = transferRepository.findItemIdById(id).orElseThrow(TransferService::transferNotFound);
        itemRepository.findForUpdateById(itemId);
        Transfer transfer = transferRepository.findById(id).orElseThrow(TransferService::transferNotFound);
        TransferRole role = requireRole(transfer, user);

        TransferStatus before = transfer.getStatus();
        if (action.test(transfer, role)) {
            record(transfer, user.id(), command, before);
        }
        return respond(transfer, user);
    }

    private void rejectRequests(Item item, Transfer except, UUID actorId) {
        for (Transfer other : transferRepository.findByItemIdAndStatus(item.getId(), TransferStatus.REQUESTED)) {
            if (!other.equals(except) && other.rejectBecauseClosed()) {
                record(other, actorId, TransferCommand.AUTO_REJECT, TransferStatus.REQUESTED);
            }
        }
    }

    /** Журнал сохраняет всю историю, а участники получают событие в своём диалоге. */
    private void record(Transfer transfer, UUID actorId, TransferCommand command, TransferStatus before) {
        eventRepository.save(new TransferEvent(
                transfer, userRepository.getReferenceById(actorId), command, before, transfer.getStatus()));
        transferRepository.flush();
        chatService.publishTransferUpdate(transfer);
    }

    private static TransferRole requireRole(Transfer transfer, CurrentUser user) {
        return transfer.roleOf(user.id()).orElseThrow(() -> new ForbiddenException("Вы не участник этой передачи"));
    }

    private TransferResponse respond(Transfer transfer, CurrentUser user) {
        transferRepository.flush();
        return toResponse(transfer, user);
    }

    private static TransferResponse toResponse(Transfer transfer, CurrentUser user) {
        return TransferResponse.from(transfer, transfer.roleOf(user.id()).orElse(null));
    }

    private static NotFoundException transferNotFound() {
        return new NotFoundException("Передача не найдена");
    }
}
