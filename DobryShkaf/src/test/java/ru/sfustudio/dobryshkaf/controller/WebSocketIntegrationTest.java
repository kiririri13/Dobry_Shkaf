package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Доставка через настоящий WebSocket: сервер на случайном порту, вход через cookie сессии. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class WebSocketIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private WeekThreeFixtures fixtures;

    private Item item;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        item = fixtures.publishedItem(fixtures.user("owner@example.com"));
        fixtures.user("alice@example.com");
        fixtures.user("stranger@example.com");
    }

    @Test
    void participantReceivesNewMessages() throws Exception {
        Client alice = new Client("alice@example.com");
        String chatId = openChat(alice);
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        subscribe(connect(new Client("owner@example.com"), new LinkedBlockingQueue<>()), chatId, events);

        String event = sendUntilDelivered(alice, chatId, events);

        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("MESSAGE_CREATED");
        assertThat((String) JsonPath.read(event, "$.payload.text")).startsWith("Привет");
    }

    @Test
    void strangerCannotSubscribeToChatEvents() throws Exception {
        Client alice = new Client("alice@example.com");
        String chatId = openChat(alice);
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        BlockingQueue<String> events = new LinkedBlockingQueue<>();

        subscribe(connect(new Client("stranger@example.com"), errors), chatId, events);

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isNotNull();
        alice.send("POST", "/api/chats/" + chatId + "/messages", messageJson("Секрет"));
        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void handshakeWithoutLoginIsRejected() {
        assertThatThrownBy(() -> connect(null, new LinkedBlockingQueue<>())).isInstanceOf(ExecutionException.class);
    }

    @Test
    void missedMessagesAreLoadedFromHistoryAfterReconnect() throws Exception {
        Client alice = new Client("alice@example.com");
        Client owner = new Client("owner@example.com");
        String chatId = openChat(alice);
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        StompSession session = connect(owner, new LinkedBlockingQueue<>());
        subscribe(session, chatId, events);
        int lastSeen = JsonPath.read(sendUntilDelivered(alice, chatId, events), "$.payload.seq");

        // Сеть пропала: пока владелец не подключён, приходят два сообщения
        session.disconnect();
        alice.send("POST", "/api/chats/" + chatId + "/messages", messageJson("Второе"));
        alice.send("POST", "/api/chats/" + chatId + "/messages", messageJson("Третье"));

        String missed = owner.send("GET", "/api/chats/" + chatId + "/messages?afterSeq=" + lastSeen, null);
        assertThat((Iterable<String>) JsonPath.read(missed, "$.messages[*].text")).containsExactly("Второе", "Третье");
    }

    /** Подписка обрабатывается асинхронно, поэтому шлём сообщения, пока одно из них не дойдёт. */
    private static String sendUntilDelivered(Client sender, String chatId, BlockingQueue<String> events) throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            sender.send("POST", "/api/chats/" + chatId + "/messages", messageJson("Привет " + attempt));
            String event = events.poll(500, TimeUnit.MILLISECONDS);
            if (event != null) {
                return event;
            }
        }
        throw new AssertionError("Событие не пришло через WebSocket");
    }

    private String openChat(Client client) throws Exception {
        return JsonPath.read(client.send("POST", "/api/chats", "{\"itemId\": \"%s\"}".formatted(item.getId())), "$.id");
    }

    private StompSession connect(Client client, BlockingQueue<String> errors) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        // Сервер шлёт application/json, а в тесте удобнее читать событие строкой
        stomp.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders messageHeaders) {
                return true;
            }
        });
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (client != null) {
            headers.add("Cookie", "JSESSIONID=" + client.cookie("JSESSIONID"));
        }
        return stomp.connectAsync("ws://localhost:" + port + "/ws", headers, new StompHeaders(), new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                errors.add("ERROR " + frameHeaders.getFirst("message"));
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errors.add("TRANSPORT " + exception);
            }
        }).get(10, TimeUnit.SECONDS);
    }

    private static void subscribe(StompSession session, String chatId, BlockingQueue<String> events) {
        session.subscribe("/topic/chats." + chatId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((String) payload);
            }
        });
    }

    private static String messageJson(String text) {
        return "{\"text\": \"%s\", \"requestId\": \"%s\"}".formatted(text, UUID.randomUUID());
    }

    /** HTTP-клиент с cookie: входит, получает CSRF-токен и отправляет его в заголовке. */
    private final class Client {

        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        Client(String email) throws Exception {
            send("POST", "/api/auth/login",
                    "{\"login\": \"%s\", \"password\": \"%s\"}".formatted(email, WeekThreeFixtures.PASSWORD));
            send("GET", "/api/auth/me", null);
        }

        String send(String method, String path, String json) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) {
                request.header("Content-Type", "application/json");
            }
            String xsrf = cookie("XSRF-TOKEN");
            if (!"GET".equals(method) && xsrf != null) {
                request.header("X-XSRF-TOKEN", xsrf);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s %s → %s", method, path, response.body()).isBetween(200, 299);
            return response.body();
        }

        String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals(name))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }
}
