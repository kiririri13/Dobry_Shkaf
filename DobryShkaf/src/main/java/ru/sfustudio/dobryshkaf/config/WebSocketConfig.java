package ru.sfustudio.dobryshkaf.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP поверх WebSocket на {@code /ws}. Рукопожатие требует входа (cookie сессии).
 * Сообщения уходят только от сервера в темы {@code /topic/chats.{id}}; отправка сообщений — через REST.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Без списка разрешённых адресов Spring принимает рукопожатие только с того же адреса,
        // поэтому чужой сайт не подключится с cookie пользователя
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatSubscriptionInterceptor);
    }
}
