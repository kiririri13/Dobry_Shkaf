package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class WeekFourIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID owner;
    private UUID receiver;
    private UUID moderator;
    private UUID admin;
    private Item item;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        User giver = fixtures.user("giver@example.com");
        owner = giver.getId();
        receiver = fixtures.user("receiver@example.com").getId();
        moderator = fixtures.user("moderator@example.com").getId();
        admin = fixtures.user("admin@example.com").getId();
        jdbc.update("update users set role = 'ADMIN' where id = ?", admin);
        jdbc.update("update users set role = 'MODERATOR' where id = ?", moderator);
        item = fixtures.publishedItem(giver);
    }

    @Test
    void favoriteAndCatalogFollowModerationAndProfileProtectsEmail() throws Exception {
        UUID district = jdbc.queryForObject("select id from districts order by id limit 1", UUID.class);
        as(owner, put("/api/profile/me"), """
                {"name":"Новое имя","districtId":"%s"}""".formatted(district))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.district.id").value(district.toString()))
                .andExpect(jsonPath("$.email").value("giver@example.com"));
        mvc.perform(get("/api/profiles/{id}", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новое имя"))
                .andExpect(jsonPath("$.email").doesNotExist());

        String favorite = "/api/profile/me/favorites/ITEMS/" + item.getId();
        as(receiver, put(favorite), null).andExpect(status().isNoContent());
        as(receiver, put(favorite), null).andExpect(status().isNoContent());
        as(receiver, get("/api/profile/me/favorites"), null).andExpect(jsonPath("$.items", hasSize(1)));
        as(receiver, get("/api/admin/items"), null).andExpect(status().isForbidden());
        as(admin, get("/api/admin/items?status=PENDING"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].photos", hasSize(1)));
        as(admin, post("/api/admin/items/{id}/review", item.getId()), null)
                .andExpect(jsonPath("$.moderationStatus").value("REVIEWED"));

        String transfer = request(item.getId(), receiver);
        as(admin, post("/api/admin/items/{id}/hide", item.getId()), "{\"reason\":\"Проверка описания\"}")
                .andExpect(jsonPath("$.moderationStatus").value("HIDDEN"));
        mvc.perform(get("/api/items/{id}", item.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/items")).andExpect(jsonPath("$.items", hasSize(0)));
        as(receiver, get("/api/profile/me/favorites"), null).andExpect(jsonPath("$.items", hasSize(0)));
        as(receiver, get("/api/transfers/{id}", transfer), null).andExpect(jsonPath("$.status").value("REJECTED"));
        as(admin, post("/api/admin/items/{id}/restore", item.getId()), null).andExpect(status().isOk());
        mvc.perform(get("/api/items/{id}", item.getId())).andExpect(status().isOk());
        as(admin, get("/api/admin/moderation-log"), null).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void complaintGrantsOnlyScopedAuditedAccessWhileOpen() throws Exception {
        String transfer = request(item.getId(), receiver);
        String chat = field(as(receiver, get("/api/transfers/{id}", transfer), null), "$.chatId");
        String message = field(send(owner, chat, "Первое сообщение").andExpect(status().isCreated()), "$.id");
        String payload = """
                {"targetType":"MESSAGE","targetId":"%s","reason":"Жалоба на сообщение"}""".formatted(message);
        as(moderator, post("/api/complaints"), payload).andExpect(status().isForbidden());
        String complaint = field(as(receiver, post("/api/complaints"), payload)
                .andExpect(status().isCreated()), "$.id");
        as(moderator, get("/api/admin/complaints?status=OPEN"), null).andExpect(jsonPath("$", hasSize(1)));
        String history = "/api/admin/complaints/" + complaint + "/chats/" + chat + "/messages";
        as(receiver, get(history), null).andExpect(status().isForbidden());
        as(moderator, get("/api/admin/complaints/{id}/chats/{chat}/messages", complaint, UUID.randomUUID()), null)
                .andExpect(status().isForbidden());
        as(moderator, get(history), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.history.messages[0].text").value("Первое сообщение"))
                .andExpect(jsonPath("$.transfers[0].id").value(transfer));
        send(receiver, chat, "Второе сообщение").andExpect(status().isCreated());
        as(moderator, get(history + "?afterSeq=1"), null).andExpect(jsonPath("$.history.messages", hasSize(1)))
                .andExpect(jsonPath("$.history.messages[0].text").value("Второе сообщение"));
        as(moderator, post("/api/admin/complaints/{id}/close", complaint),
                "{\"outcome\":\"UPHELD\",\"explanation\":\"Проверено\"}").andExpect(status().isOk());
        as(moderator, get(history), null).andExpect(status().isForbidden());
        as(moderator, post("/api/admin/complaints/{id}/reopen", complaint), null).andExpect(status().isOk());
        as(moderator, get(history + "?limit=1"), null).andExpect(jsonPath("$.history.hasMore").value(true));
        as(admin, get("/api/admin/moderation-log"), null)
                .andExpect(jsonPath("$[?(@.action == 'VIEW_CHAT')]", hasSize(3)))
                .andExpect(jsonPath("$[?(@.outcome == 'UPHELD')]", hasSize(1)));
        jdbc.update("update users set role = 'USER' where id = ?", moderator);
        as(moderator, get(history), null).andExpect(status().isForbidden());

        as(admin, post("/api/admin/users/{id}/block", receiver), "{\"reason\":\"Нарушение\"}")
                .andExpect(status().isNoContent());
        send(receiver, chat, "Сообщение после блокировки").andExpect(status().isForbidden());
        as(receiver, post("/api/items"), "{\"title\":\"Вещь\"}").andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"receiver@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewsRequireCompletedTransferAndHiddenReviewStopsCounting() throws Exception {
        String transfer = request(item.getId(), receiver);
        String body = "{\"rating\":5,\"text\":\"Спасибо!\"}";
        as(owner, post("/api/transfers/{id}/reviews", transfer), body).andExpect(status().isConflict());
        as(owner, post("/api/transfers/{id}/select", transfer), null).andExpect(status().isOk());
        as(owner, post("/api/transfers/{id}/confirm", transfer), null).andExpect(status().isOk());
        as(receiver, post("/api/transfers/{id}/confirm", transfer), null).andExpect(status().isOk());
        as(receiver, post("/api/transfers/{id}/reviews", transfer), "{\"rating\":6}").andExpect(status().isBadRequest());
        String review = field(as(receiver, post("/api/transfers/{id}/reviews", transfer), body)
                .andExpect(status().isCreated()), "$.id");
        as(receiver, post("/api/transfers/{id}/reviews", transfer), body).andExpect(status().isConflict());
        as(owner, get("/api/profile/me/history"), null).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/profiles/{id}", owner)).andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.completedTransfers").value(1));
        mvc.perform(get("/api/profiles/{id}", receiver)).andExpect(jsonPath("$.completedTransfers").value(1));
        mvc.perform(get("/api/stats")).andExpect(jsonPath("$.completedTransfers").value(1));
        as(admin, post("/api/admin/reviews/{id}/hide", review), "{\"reason\":\"Оскорбление\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/profiles/{id}", owner)).andExpect(jsonPath("$.rating").doesNotExist());
        mvc.perform(get("/api/profiles/{id}/reviews", owner)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void contributionSurvivesRetryReopenAndCoefficientUpdateWithoutCountingOriginTwice() throws Exception {
        as(owner, put("/api/items/{id}/eco", item.getId()), """
                {"lines":[
                    {"category":"COTTON_TSHIRT","quantity":2,"weightKg":0.2,"weightMode":"PER_UNIT"},
                    {"category":"JEANS","quantity":1}
                ]}""").andExpect(status().isOk());
        close(item.getId());
        close(item.getId());
        mvc.perform(get("/api/stats")).andExpect(jsonPath("$.closedListings").value(1))
                .andExpect(jsonPath("$.contribution.weightKg").value(0.4))
                .andExpect(jsonPath("$.contribution.co2eKg").value(33.6));

        String line = field(as(owner, get("/api/items/{id}/eco", item.getId()), null), "$.credits[0].lineId");
        Item duplicate = fixtures.publishedItem(item.getOwner());
        as(owner, put("/api/items/{id}/eco", duplicate.getId()), """
                {"lines":[{"previousLineId":"%s"},{"category":"UNKNOWN","quantity":2}]}""".formatted(line))
                .andExpect(status().isOk());
        close(duplicate.getId());
        mvc.perform(get("/api/stats")).andExpect(jsonPath("$.closedListings").value(2))
                .andExpect(jsonPath("$.contribution.co2eKg").value(33.6))
                .andExpect(jsonPath("$.contribution.countedItems").value(5))
                .andExpect(jsonPath("$.estimateNotice").exists());

        jdbc.update("""
                insert into eco_coefficients (category, version, coefficient, source)
                select category, 2, 6.0, source from eco_coefficients where category = 'COTTON_TSHIRT' and version = 1
                on conflict do nothing
                """);
        try {
            as(owner, post("/api/items/{id}/reopen", item.getId()), null).andExpect(status().isOk());
            mvc.perform(get("/api/stats")).andExpect(jsonPath("$.contribution.co2eKg").doesNotExist());
            close(item.getId());
            mvc.perform(get("/api/stats")).andExpect(jsonPath("$.contribution.co2eKg").value(33.6));
            as(admin, post("/api/admin/eco/items/{id}/recalculate", item.getId()), "{\"reason\":\"Новая версия\"}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].coefficientVersion").value(2));
            as(owner, get("/api/items/{id}/eco", item.getId()), null)
                    .andExpect(jsonPath("$.credits[0].coefficientVersion").value(1));
            mvc.perform(get("/api/stats")).andExpect(jsonPath("$.contribution.co2eKg").value(35.2));
        } finally {
            jdbc.update("delete from eco_coefficients where category = 'COTTON_TSHIRT' and version = 2");
        }
    }

    @Test
    void pointFavoritesAndBatchAccountingAreIdempotent() throws Exception {
        UUID organization = UUID.randomUUID();
        UUID point = UUID.randomUUID();
        jdbc.update("insert into organizations (id, name, created_at, updated_at) values (?, ?, now(), now())",
                organization, "Тестовая организация " + organization);
        jdbc.update("""
                insert into collection_points (id, organization_id, district_id, point_type, name, address,
                    latitude, longitude, hours, contacts, conditions, source, verified_at, status, created_at, updated_at)
                select ?, ?, id, 'COLLECTION', 'Контейнер', 'Адрес', 56.8, 60.6, 'Ежедневно', 'Контакты',
                    'Текстиль', 'Тест', current_date, 'ACTIVE', now(), now() from districts limit 1
                """, point, organization);

        String favorite = "/api/profile/me/favorites/POINTS/" + point;
        as(receiver, put(favorite), null).andExpect(status().isNoContent());
        as(receiver, put(favorite), null).andExpect(status().isNoContent());
        as(receiver, get("/api/profile/me/favorites"), null).andExpect(jsonPath("$.points", hasSize(1)));
        as(receiver, delete(favorite), null).andExpect(status().isNoContent());
        as(receiver, get("/api/profile/me/favorites"), null).andExpect(jsonPath("$.points", hasSize(0)));

        String collection = """
                {"pointId":"%s","kind":"CONTAINER_COLLECTION","quantity":2,"weightKg":0.4}""".formatted(point);
        String distribution = collection.replace("CONTAINER_COLLECTION", "CONFIRMED_DISTRIBUTION");
        String batch = "/api/admin/eco/point-batches/" + UUID.randomUUID();
        as(admin, put(batch), collection).andExpect(status().isOk());
        as(admin, put(batch), collection).andExpect(status().isOk());
        as(admin, put(batch), distribution).andExpect(status().isConflict());
        as(admin, put("/api/admin/eco/point-batches/{id}", UUID.randomUUID()), distribution)
                .andExpect(status().isOk());
        mvc.perform(get("/api/stats")).andExpect(jsonPath("$.pointBatches", hasSize(2)))
                .andExpect(jsonPath("$.pointBatches[?(@.batches == 1)]", hasSize(2)))
                .andExpect(jsonPath("$.methodologyUrl").value("/api/eco/methodology"));
    }

    private void close(UUID id) throws Exception {
        as(owner, post("/api/items/{id}/close", id), null).andExpect(status().isOk());
    }

    private String request(UUID itemId, UUID actor) throws Exception {
        return field(as(actor, post("/api/transfers"), "{\"itemId\":\"%s\"}".formatted(itemId))
                .andExpect(status().isCreated()), "$.id");
    }

    private ResultActions send(UUID actor, String chat, String text) throws Exception {
        return as(actor, post("/api/chats/{id}/messages", chat), """
                {"requestId":"%s","text":"%s"}""".formatted(UUID.randomUUID(), text));
    }

    private ResultActions as(UUID actor, MockHttpServletRequestBuilder request, String body) throws Exception {
        String role = actor.equals(admin) ? "ADMIN" : actor.equals(moderator) ? "MODERATOR" : "USER";
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request.with(user(actor.toString()).roles(role)).with(csrf()));
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
