package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfustudio.dobryshkaf.domain.Chat;
import ru.sfustudio.dobryshkaf.domain.ChatMessage;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.dto.ChatMessageResponse;
import ru.sfustudio.dobryshkaf.dto.ChatResponse;
import ru.sfustudio.dobryshkaf.dto.ItemSummaryResponse;
import ru.sfustudio.dobryshkaf.dto.MessagePageResponse;
import ru.sfustudio.dobryshkaf.dto.MessagesReadResponse;
import ru.sfustudio.dobryshkaf.dto.SendMessageRequest;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.dto.UserSummaryResponse;
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.exception.FieldValidationException;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.ChatMessageRepository;
import ru.sfustudio.dobryshkaf.repository.ChatRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;

    private final ChatRepository chatRepository;
    private final ChatMessageRepository messageRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    /** Один диалог на пару «объявление и пользователь»: повторное открытие возвращает существующий. */
    @Transactional
    public CommandResult<ChatResponse> open(UUID itemId, CurrentUser user) {
        Optional<Chat> existing = chatRepository.findByItemIdAndRequesterId(itemId, user.id());
        if (existing.isPresent()) {
            return new CommandResult<>(toResponse(existing.get(), user.id()), false);
        }
        Item item = itemRepository.findForUpdateById(itemId)
                .filter(Item::isPublic)
                .orElseThrow(() -> new NotFoundException("Объявление не найдено"));
        if (item.isOwnedBy(user.id())) {
            throw new ConflictException("OWN_ITEM", "Нельзя открыть диалог по собственной вещи");
        }
        // Пока строка вещи заблокирована, параллельный такой же запрос ждёт и потом найдёт уже созданный диалог
        Optional<Chat> createdMeanwhile = chatRepository.findByItemIdAndRequesterId(itemId, user.id());
        Chat chat = createdMeanwhile.orElseGet(() -> createChat(item, user.id()));
        return new CommandResult<>(toResponse(chat, user.id()), createdMeanwhile.isEmpty());
    }

    /** Диалог для передачи вещи; строку вещи уже заблокировал вызывающий код. */
    Chat getOrCreateFor(Item lockedItem, UUID requesterId) {
        return chatRepository.findByItemIdAndRequesterId(lockedItem.getId(), requesterId)
                .orElseGet(() -> createChat(lockedItem, requesterId));
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> list(CurrentUser user) {
        return chatRepository.findAllForParticipant(user.id()).stream()
                .map(chat -> toResponse(chat, user.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatResponse get(UUID chatId, CurrentUser user) {
        return toResponse(findAccessible(chatId, user), user.id());
    }

    /**
     * История частями. Без курсора — последние сообщения; {@code beforeSeq} — более старые;
     * {@code afterSeq} — всё, что пришло после этого номера (догрузка после переподключения).
     */
    @Transactional(readOnly = true)
    public MessagePageResponse messages(UUID chatId, CurrentUser user, Long beforeSeq, Long afterSeq, Integer limit) {
        Chat chat = findAccessible(chatId, user);
        if (beforeSeq != null && afterSeq != null) {
            throw new FieldValidationException("VALIDATION_FAILED", "Проверьте параметры запроса",
                    Map.of("afterSeq", "Укажите либо beforeSeq, либо afterSeq"));
        }
        int size = pageSize(limit);
        PageRequest window = PageRequest.of(0, size + 1);
        List<ChatMessage> found = afterSeq != null
                ? messageRepository.findByChatIdAndSeqGreaterThanOrderBySeqAsc(chat.getId(), afterSeq, window)
                : messageRepository.findByChatIdAndSeqLessThanOrderBySeqDesc(
                        chat.getId(), beforeSeq == null ? Long.MAX_VALUE : beforeSeq, window);

        boolean hasMore = found.size() > size;
        List<ChatMessage> page = new ArrayList<>(hasMore ? found.subList(0, size) : found);
        if (afterSeq == null) {
            // Выбирали от новых к старым, а отдаём в порядке чтения
            Collections.reverse(page);
        }
        return new MessagePageResponse(page.stream().map(ChatMessageResponse::from).toList(), hasMore);
    }

    /** Повтор с тем же requestId возвращает уже сохранённое сообщение и не рассылает его второй раз. */
    @Transactional
    public CommandResult<ChatMessageResponse> send(UUID chatId, SendMessageRequest request, CurrentUser user) {
        Chat chat = chatRepository.findForUpdateById(chatId).orElseThrow(ChatService::chatNotFound);
        requireParticipant(chat, user);
        Optional<ChatMessage> repeated =
                messageRepository.findByChatIdAndSenderIdAndRequestId(chatId, user.id(), request.requestId());
        if (repeated.isPresent()) {
            return new CommandResult<>(ChatMessageResponse.from(repeated.get()), false);
        }

        ChatMessage message = messageRepository.save(ChatMessage.send(
                chat, userRepository.getReferenceById(user.id()), request.text(), request.requestId()));
        messageRepository.flush();
        ChatMessageResponse response = ChatMessageResponse.from(message);
        publish(chatId, ChatEventType.MESSAGE_CREATED, response);
        return new CommandResult<>(response, true);
    }

    @Transactional
    public void markRead(UUID chatId, long upToSeq, CurrentUser user) {
        Chat chat = findAccessible(chatId, user);
        if (messageRepository.markRead(chat.getId(), user.id(), upToSeq, Instant.now()) > 0) {
            publish(chatId, ChatEventType.MESSAGES_READ, new MessagesReadResponse(user.id(), upToSeq));
        }
    }

    /** Проверка подписки на тему диалога через WebSocket. */
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID chatId, UUID userId) {
        return chatRepository.isParticipant(chatId, userId);
    }

    void publishTransferUpdate(Transfer transfer) {
        publish(transfer.getChat().getId(), ChatEventType.TRANSFER_UPDATED, TransferResponse.from(transfer, null));
    }

    private Chat createChat(Item item, UUID requesterId) {
        Chat chat = chatRepository.save(new Chat(item, userRepository.getReferenceById(requesterId)));
        chatRepository.flush();
        return chat;
    }

    private Chat findAccessible(UUID chatId, CurrentUser user) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(ChatService::chatNotFound);
        requireParticipant(chat, user);
        return chat;
    }

    private static void requireParticipant(Chat chat, CurrentUser user) {
        if (!chat.isParticipant(user.id())) {
            throw new ForbiddenException("Это не ваш диалог");
        }
    }

    private ChatResponse toResponse(Chat chat, UUID viewerId) {
        ChatMessageResponse lastMessage = messageRepository.findFirstByChatIdOrderBySeqDesc(chat.getId())
                .map(ChatMessageResponse::from)
                .orElse(null);
        long unread = messageRepository.countByChatIdAndSenderIdNotAndReadAtIsNull(chat.getId(), viewerId);
        return new ChatResponse(
                chat.getId(),
                ItemSummaryResponse.from(chat.getItem()),
                UserSummaryResponse.from(chat.counterpartOf(viewerId)),
                lastMessage,
                unread,
                chat.getCreatedAt(),
                chat.getUpdatedAt());
    }

    private void publish(UUID chatId, ChatEventType type, Object payload) {
        events.publishEvent(new ChatEventPublished(chatId, new ChatEvent(type, payload)));
    }

    private static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new FieldValidationException("VALIDATION_FAILED", "Проверьте параметры запроса",
                    Map.of("limit", "Можно запросить от 1 до " + MAX_PAGE_SIZE + " сообщений"));
        }
        return limit;
    }

    private static NotFoundException chatNotFound() {
        return new NotFoundException("Диалог не найден");
    }
}
