package ru.sfustudio.dobryshkaf.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import ru.sfustudio.dobryshkaf.service.ChatService;
import ru.sfustudio.dobryshkaf.service.ChatTopics;

import java.security.Principal;
import java.util.UUID;

/**
 * Пускает подписку на тему диалога только его участников. Исключение превращается в STOMP-кадр ERROR,
 * и сервер закрывает соединение.
 */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> requireUser(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new AccessDeniedException("Сообщения отправляются через POST /api/chats/{id}/messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE и служебные кадры пропускаем
            }
        }
        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        UUID userId = requireUser(accessor);
        UUID chatId = ChatTopics.chatIdOf(accessor.getDestination())
                .orElseThrow(() -> new AccessDeniedException("Подписаться можно только на /topic/chats.{id}"));
        if (!chatService.isParticipant(chatId, userId)) {
            throw new AccessDeniedException("Нет доступа к этому диалогу");
        }
    }

    private static UUID requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("Нужен вход");
        }
        return UUID.fromString(user.getName());
    }
}
