package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    private User owner;
    private User alice;
    private User stranger;
    private Item item;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        owner = fixtures.user("owner@example.com");
        alice = fixtures.user("alice@example.com");
        stranger = fixtures.user("stranger@example.com");
        item = fixtures.publishedItem(owner);
    }

    @Test
    void reopeningReturnsExistingChat() throws Exception {
        String chatId = openChat();

        open(alice).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(chatId));
        open(owner).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWN_ITEM"));
        perform(get("/api/chats"), owner)
                .andExpect(jsonPath("$[0].id").value(chatId))
                .andExpect(jsonPath("$[0].counterpart.name").value("alice"));
    }

    @Test
    void strangerCannotReadOrWrite() throws Exception {
        String chatId = openChat();

        perform(get("/api/chats/{id}", chatId), stranger).andExpect(status().isForbidden());
        perform(get("/api/chats/{id}/messages", chatId), stranger).andExpect(status().isForbidden());
        send(stranger, chatId, "Привет", UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void repeatedSendWithSameRequestIdReturnsSameMessage() throws Exception {
        String chatId = openChat();
        UUID requestId = UUID.randomUUID();

        String first = send(alice, chatId, "Добрый день", requestId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(1))
                .andReturn().getResponse().getContentAsString();
        send(alice, chatId, "Добрый день", requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")))
                .andExpect(jsonPath("$.seq").value(1));

        perform(get("/api/chats/{id}/messages", chatId), owner).andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void validatesMessageText() throws Exception {
        String chatId = openChat();

        send(alice, chatId, "   ", UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.text").exists());
        send(alice, chatId, "a".repeat(2001), UUID.randomUUID()).andExpect(status().isBadRequest());
        send(alice, chatId, "a".repeat(2000), UUID.randomUUID()).andExpect(status().isCreated());
        perform(post("/api/chats/{id}/messages", chatId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"Без идентификатора\"}"), alice)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.requestId").exists());
    }

    @Test
    void historyIsReturnedInPagesAndMissedMessagesAfterSeq() throws Exception {
        String chatId = openChat();
        for (int i = 1; i <= 5; i++) {
            send(i % 2 == 0 ? owner : alice, chatId, "Сообщение " + i, UUID.randomUUID());
        }

        perform(get("/api/chats/{id}/messages", chatId).param("limit", "2"), alice)
                .andExpect(jsonPath("$.messages[*].seq").value(contains(4, 5)))
                .andExpect(jsonPath("$.hasMore").value(true));
        perform(get("/api/chats/{id}/messages", chatId).param("beforeSeq", "4").param("limit", "2"), alice)
                .andExpect(jsonPath("$.messages[*].seq").value(contains(2, 3)));
        perform(get("/api/chats/{id}/messages", chatId).param("afterSeq", "3"), alice)
                .andExpect(jsonPath("$.messages[*].seq").value(contains(4, 5)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void readMarksAreStored() throws Exception {
        String chatId = openChat();
        send(alice, chatId, "Первое", UUID.randomUUID());
        send(alice, chatId, "Второе", UUID.randomUUID());
        perform(get("/api/chats"), owner).andExpect(jsonPath("$[0].unreadCount").value(2));

        perform(post("/api/chats/{id}/read", chatId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"upToSeq\": 2}"), owner)
                .andExpect(status().isNoContent());

        perform(get("/api/chats"), owner).andExpect(jsonPath("$[0].unreadCount").value(0));
        perform(get("/api/chats/{id}/messages", chatId), alice)
                .andExpect(jsonPath("$.messages[0].readAt").exists())
                .andExpect(jsonPath("$.messages[1].readAt").exists());
    }

    private String openChat() throws Exception {
        return JsonPath.read(open(alice).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions open(User actor) throws Exception {
        return perform(post("/api/chats").contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": \"%s\"}".formatted(item.getId())), actor);
    }

    private ResultActions send(User actor, String chatId, String text, UUID requestId) throws Exception {
        return perform(post("/api/chats/{id}/messages", chatId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"%s\", \"requestId\": \"%s\"}".formatted(text, requestId)), actor);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(request.with(user(actor.getId().toString()).roles("USER")).with(csrf()));
    }
}
