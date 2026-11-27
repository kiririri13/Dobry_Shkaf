package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;
import ru.sfustudio.dobryshkaf.storage.PhotoStorage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class FrontendAdditionsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PhotoStorage photoStorage;

    private User owner;
    private Actors actors;
    private Item item;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        owner = fixtures.user("owner@example.com");
        actors = new Actors(fixtures.user("reader@example.com").getId(), fixtures.user("admin@example.com").getId());
        jdbc.update("update users set role = 'ADMIN' where id = ?", actors.admin());
        item = fixtures.publishedItem(owner);
    }

    @Test
    void deliveryMethodAndPublicHistoryUseCompletedTransfersOnly() throws Exception {
        mvc.perform(get("/api/items/{id}", item.getId()))
                .andExpect(jsonPath("$.deliveryMethod").value("FREE"));
        UUID district = jdbc.queryForObject("select district_id from items where id = ?", UUID.class, item.getId());
        UUID category = jdbc.queryForObject("select category_id from items where id = ?", UUID.class, item.getId());
        String edit = """
                {"title":"Книга","description":"Описание","condition":"NEW","size":"M","categoryId":"%s","districtId":"%s"}""";
        String withMethod = edit.formatted(category, district).replace("}", ", \"deliveryMethod\":\"EXCHANGE\"}");
        as(owner.getId(), put("/api/items/{id}", item.getId()), withMethod)
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryMethod").value("EXCHANGE"));
        as(owner.getId(), put("/api/items/{id}", item.getId()), edit.formatted(category, district))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryMethod").value("EXCHANGE"));

        String transfer = requestTransfer();
        mvc.perform(get("/api/profiles/{id}/history", owner.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        completeTransfer(transfer);
        byte[] image = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        photoStorage.put(item.getPhotos().getFirst().getStorageKey(), image, "image/jpeg");
        ResultActions history = mvc.perform(get("/api/profiles/{id}/history", owner.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("GIVER"))
                .andExpect(jsonPath("$.items[0].deliveryMethod").value("EXCHANGE"))
                .andExpect(jsonPath("$.items[0].chatId").doesNotExist())
                .andExpect(jsonPath("$.items[0].receiver").doesNotExist())
                .andExpect(jsonPath("$.items[0].size").value("M"));
        String photoUrl = field(history, "$.items[0].photoUrl");
        mvc.perform(get(photoUrl)).andExpect(status().isOk()).andExpect(content().bytes(image));
        as(owner.getId(), get("/api/profile/me/history"), null)
                .andExpect(jsonPath("$[0].item.size").value("M"))
                .andExpect(jsonPath("$[0].item.photoUrl").value(photoUrl));
        mvc.perform(get("/api/items/{id}", item.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/{id}/history", actors.reader()))
                .andExpect(jsonPath("$.items[0].role").value("RECEIVER"));
        jdbc.update("update items set moderation_status = 'HIDDEN' where id = ?", item.getId());
        mvc.perform(get(photoUrl)).andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/{id}/history", owner.getId()))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void avatarIsPublicAndCanBeReplacedByOwner() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
        MockMultipartFile first = new MockMultipartFile("file", "avatar.png", "image/png", png);
        String url = field(as(owner.getId(), multipart(HttpMethod.PUT, "/api/profile/me/avatar").file(first), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberSinceYear").isNumber())
                .andExpect(jsonPath("$.registeredAt").exists()), "$.avatarUrl");
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(png));
        png[png.length - 1] = 2;
        MockMultipartFile replacement = new MockMultipartFile("file", "avatar.png", "image/png", png);
        as(owner.getId(), multipart(HttpMethod.PUT, "/api/profile/me/avatar").file(replacement), null)
                .andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(content().bytes(png));
        as(owner.getId(), delete("/api/profile/me/avatar"), null).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void chatFileIsStoredOnceAndAvailableOnlyToParticipants() throws Exception {
        String chat = field(as(actors.reader(), post("/api/chats"), itemBody(item))
                .andExpect(status().isCreated()), "$.id");
        UUID requestId = UUID.randomUUID();
        byte[] bytes = "contents of attachment".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "описание.txt", "text/plain", bytes);
        String upload = "/api/chats/" + chat + "/messages/attachments";
        String url = field(as(actors.reader(), multipart(upload).file(file).param("requestId", requestId.toString()), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attachment.name").value("описание.txt"))
                .andExpect(jsonPath("$.text").value("")), "$.attachment.url");
        as(actors.reader(), multipart(upload).file(file).param("requestId", requestId.toString()), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachment.url").value(url));
        as(owner.getId(), get("/api/chats/{id}/messages", chat), null)
                .andExpect(jsonPath("$.messages", hasSize(1))).andExpect(jsonPath("$.messages[0].attachment.url").value(url));
        as(owner.getId(), get(url), null).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM)).andExpect(content().bytes(bytes));
        as(actors.admin(), get(url), null).andExpect(status().isForbidden());
    }

    @Test
    void notificationsTrackReviewReadStateAndModerationDecisions() throws Exception {
        String complaint = field(as(actors.reader(), post("/api/complaints"), """
                {"targetType":"ITEM","targetId":"%s","reason":"Описание"}""".formatted(item.getId()))
                .andExpect(status().isCreated()), "$.id");
        as(actors.admin(), post("/api/admin/complaints/{id}/close", complaint),
                "{\"outcome\":\"UPHELD\",\"explanation\":\"Проверено\"}").andExpect(status().isOk());
        as(actors.reader(), get("/api/complaints/{id}", complaint), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UPHELD"));
        as(owner.getId(), get("/api/complaints/{id}", complaint), null).andExpect(status().isNotFound());
        as(actors.admin(), post("/api/admin/items/{id}/review", item.getId()), null).andExpect(status().isOk());
        String transfer = requestTransfer();
        completeTransfer(transfer);
        String review = field(as(actors.reader(), post("/api/transfers/{id}/reviews", transfer), "{\"rating\":5}")
                .andExpect(status().isCreated()), "$.id");

        String notification = field(as(owner.getId(), get("/api/notifications"), null)
                .andExpect(jsonPath("$.unreadCount").value(4))
                .andExpect(jsonPath("$.items[0].type").value("REVIEW_RECEIVED")), "$.items[0].id");
        as(actors.reader(), put("/api/notifications/{id}/read", notification), null).andExpect(status().isNotFound());
        as(owner.getId(), get("/api/profile/me/reviews"), null).andExpect(jsonPath("$[0].readAt").doesNotExist());
        as(owner.getId(), put("/api/profile/me/reviews/{id}/read", review), null).andExpect(status().isNoContent());
        as(owner.getId(), put("/api/profile/me/reviews/{id}/read", review), null).andExpect(status().isNoContent());
        as(owner.getId(), get("/api/profile/me/reviews"), null).andExpect(jsonPath("$[0].readAt").exists());
        as(owner.getId(), get("/api/notifications?unreadOnly=true"), null).andExpect(jsonPath("$.items", hasSize(3)));
        as(actors.reader(), get("/api/notifications"), null)
                .andExpect(jsonPath("$.items[?(@.type == 'COMPLAINT_UPHELD')].detail").value(contains("Проверено")));
        as(owner.getId(), put("/api/notifications/read-all"), null).andExpect(status().isNoContent());
        as(owner.getId(), get("/api/notifications"), null).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void contributionCountsItemsByCatalogCategoryAndRemovesReopenedListing() throws Exception {
        Item toys = fixtures.publishedItem(owner);
        jdbc.update("update items set category_id = (select id from categories where code = 'toys') where id = ?", toys.getId());
        as(owner.getId(), put("/api/items/{id}/eco", toys.getId()), """
                {"lines":[{"category":"TOY","quantity":3}]}""").andExpect(status().isOk());
        as(owner.getId(), post("/api/items/{id}/close", toys.getId()), null).andExpect(status().isOk());
        as(owner.getId(), post("/api/items/{id}/close", item.getId()), null).andExpect(status().isOk());
        mvc.perform(get("/api/stats"))
                .andExpect(jsonPath("$.byCategory[?(@.code == 'toys')].quantity").value(contains(3)))
                .andExpect(jsonPath("$.byCategory[?(@.code == 'books')].quantity").value(contains(1)));
        as(actors.reader(), get("/api/profile/me/stats"), null).andExpect(jsonPath("$.byCategory", hasSize(0)));

        as(owner.getId(), post("/api/items/{id}/reopen", toys.getId()), null).andExpect(status().isOk());
        as(owner.getId(), get("/api/profile/me/stats"), null).andExpect(jsonPath("$.byCategory", hasSize(1)));
        as(owner.getId(), get("/api/notifications"), null)
                .andExpect(jsonPath("$.items[?(@.type == 'ITEM_UNPUBLISHED')]", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.type == 'ITEM_PUBLISHED')]", hasSize(1)));
    }

    @Test
    void publicProfileShowsBioAndOnlyVisibleItemsOwnedByThatUser() throws Exception {
        as(owner.getId(), put("/api/profile/me"), "{\"name\":\"Анна\",\"bio\":\"  Отдаю книги  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.bio").value("Отдаю книги"));
        as(owner.getId(), put("/api/profile/me"), "{\"name\":\"Анна\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.bio").value("Отдаю книги"));
        mvc.perform(get("/api/profiles/{id}", owner.getId())).andExpect(jsonPath("$.bio").value("Отдаю книги"));

        as(owner.getId(), post("/api/items"), "{\"title\":\"Мой черновик\"}").andExpect(status().isCreated());
        Item hidden = fixtures.publishedItem(owner);
        jdbc.update("update items set moderation_status = 'HIDDEN' where id = ?", hidden.getId());
        fixtures.publishedItem(fixtures.user("another@example.com"));
        mvc.perform(get("/api/profiles/{id}/items", owner.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(item.getId().toString()));
        as(owner.getId(), put("/api/profile/me"), "{\"name\":\"Анна\",\"bio\":\" \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.bio").doesNotExist());
    }

    @Test
    void favoritesAndComplaintsNotifyOwnerWithoutRevealingReporter() throws Exception {
        String favorite = "/api/profile/me/favorites/ITEMS/" + item.getId();
        as(actors.reader(), put(favorite), null).andExpect(status().isNoContent());
        as(actors.reader(), put(favorite), null).andExpect(status().isNoContent());
        as(owner.getId(), put(favorite), null).andExpect(status().isNoContent());

        as(actors.reader(), post("/api/complaints"), "{\"targetType\":\"ITEM\",\"targetId\":\"%s\",\"reason\":\"Приватная причина\"}"
                .formatted(item.getId())).andExpect(status().isCreated());
        as(actors.reader(), post("/api/complaints"), "{\"targetType\":\"PROFILE\",\"targetId\":\"%s\",\"reason\":\"Приватная причина\"}"
                .formatted(owner.getId())).andExpect(status().isCreated());

        as(owner.getId(), get("/api/notifications"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.items[?(@.type == 'ITEM_FAVORITED')].entityId").value(contains(item.getId().toString())))
                .andExpect(jsonPath("$.items[?(@.type == 'ITEM_REPORTED')].entityId").value(contains(item.getId().toString())))
                .andExpect(jsonPath("$.items[?(@.type == 'PROFILE_REPORTED')].entityId").value(contains(owner.getId().toString())))
                .andExpect(jsonPath("$.items[*].reporterId", hasSize(0)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Приватная причина"))));
    }

    private String requestTransfer() throws Exception {
        return field(as(actors.reader(), post("/api/transfers"), itemBody(item)).andExpect(status().isCreated()), "$.id");
    }

    private void completeTransfer(String id) throws Exception {
        as(owner.getId(), post("/api/transfers/{id}/select", id), null).andExpect(status().isOk());
        as(actors.reader(), post("/api/transfers/{id}/confirm", id), null).andExpect(status().isOk());
        as(owner.getId(), post("/api/transfers/{id}/confirm", id), null).andExpect(status().isOk());
    }

    private static String itemBody(Item item) {
        return "{\"itemId\":\"%s\"}".formatted(item.getId());
    }

    private ResultActions as(UUID actor, AbstractMockHttpServletRequestBuilder<?> req, String body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req.with(user(actor.toString()).roles(actor.equals(actors.admin()) ? "ADMIN" : "USER")).with(csrf()));
    }

    private static String field(ResultActions response, String expression) throws Exception {
        return JsonPath.read(response.andReturn().getResponse().getContentAsString(), expression);
    }

    private record Actors(UUID reader, UUID admin) {
    }
}
