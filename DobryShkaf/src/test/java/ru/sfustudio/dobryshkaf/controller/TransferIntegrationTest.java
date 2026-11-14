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
import ru.sfustudio.dobryshkaf.exception.ConflictException;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.TransferService;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class TransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User alice;
    private User bob;
    private User stranger;
    private Item item;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        owner = fixtures.user("owner@example.com");
        alice = fixtures.user("alice@example.com");
        bob = fixtures.user("bob@example.com");
        stranger = fixtures.user("stranger@example.com");
        item = fixtures.publishedItem(owner);
    }

    @Test
    void fullCycleFromRequestToCompletedTransfer() throws Exception {
        String aliceTransfer = request(alice).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.chatId").exists())
                .andReturn().getResponse().getContentAsString();
        String transferId = JsonPath.read(aliceTransfer, "$.id");
        // Повтор запроса возвращает прежний результат
        request(alice).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transferId));
        String bobTransferId = JsonPath.read(request(bob).andReturn().getResponse().getContentAsString(), "$.id");
        itemStatus().andExpect(jsonPath("$.status").value("PUBLISHED"));

        command(owner, transferId, "select")
                .andExpect(jsonPath("$.status").value("RESERVED"));
        perform(get("/api/transfers/{id}", bobTransferId), bob).andExpect(jsonPath("$.status").value("REJECTED"));
        itemStatus().andExpect(jsonPath("$.status").value("RESERVED"));

        command(alice, transferId, "confirm").andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"));
        command(alice, transferId, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"));
        command(owner, transferId, "confirm")
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
        itemStatus().andExpect(jsonPath("$.status").value("TRANSFERRED"));

        perform(get("/api/transfers/{id}/events", transferId), alice)
                .andExpect(jsonPath("$[*].command").value(org.hamcrest.Matchers.contains("REQUEST", "SELECT", "CONFIRM", "CONFIRM")));
    }

    @Test
    void strangerIsRejectedForAnyCommand() throws Exception {
        String transferId = JsonPath.read(request(alice).andReturn().getResponse().getContentAsString(), "$.id");

        for (String action : List.of("select", "confirm", "cancel", "cancellation/accept", "cancellation/decline")) {
            command(stranger, transferId, action).andExpect(status().isForbidden());
        }
        perform(get("/api/transfers/{id}", transferId), stranger).andExpect(status().isForbidden());
        perform(get("/api/transfers/{id}/events", transferId), stranger).andExpect(status().isForbidden());
        command(alice, transferId, "select").andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotRequestOwnItemAndReservedItemTakesNoNewRequests() throws Exception {
        request(owner).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWN_ITEM"));

        String transferId = JsonPath.read(request(alice).andReturn().getResponse().getContentAsString(), "$.id");
        command(owner, transferId, "select");

        request(bob).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_NOT_AVAILABLE"));
    }

    @Test
    void confirmationIsRejectedDuringCancellationNegotiation() throws Exception {
        String transferId = JsonPath.read(request(alice).andReturn().getResponse().getContentAsString(), "$.id");
        command(owner, transferId, "select");
        command(alice, transferId, "confirm");

        command(owner, transferId, "cancel").andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"));
        command(alice, transferId, "confirm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_STATE_CONFLICT"));
        command(owner, transferId, "confirm").andExpect(status().isConflict());

        command(alice, transferId, "cancellation/decline").andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"));
        command(owner, transferId, "cancel");
        command(alice, transferId, "cancellation/accept").andExpect(jsonPath("$.status").value("CANCELLED"));
        itemStatus().andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void cancelledReservationReturnsItemToCatalog() throws Exception {
        String transferId = JsonPath.read(request(alice).andReturn().getResponse().getContentAsString(), "$.id");
        command(owner, transferId, "select");

        command(alice, transferId, "cancel").andExpect(jsonPath("$.status").value("CANCELLED"));

        itemStatus().andExpect(jsonPath("$.status").value("PUBLISHED"));
        request(bob).andExpect(status().isCreated());
    }

    @Test
    void twoSimultaneousSelectionsGiveOneReservation() throws Exception {
        UUID aliceTransfer = transferService.request(item.getId(), as(alice)).body().id();
        UUID bobTransfer = transferService.request(item.getId(), as(bob)).body().id();

        List<Object> results = runConcurrently(
                () -> transferService.select(aliceTransfer, as(owner)),
                () -> transferService.select(bobTransfer, as(owner)));

        assertThat(results).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transfers where status = 'RESERVED'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transfers where status = 'REJECTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void twoSimultaneousConfirmationsCreateOneCompletedTransfer() throws Exception {
        UUID transferId = transferService.request(item.getId(), as(alice)).body().id();
        transferService.select(transferId, as(owner));

        List<Object> results = runConcurrently(
                () -> transferService.confirm(transferId, as(owner)),
                () -> transferService.confirm(transferId, as(alice)));

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(jdbcTemplate.queryForObject(
                "select status from transfers where id = ?", String.class, transferId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from items where id = ?", String.class, item.getId())).isEqualTo("TRANSFERRED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transfer_events where transfer_id = ? and to_status = 'COMPLETED'",
                Integer.class, transferId)).isEqualTo(1);
    }

    /** Запускает задачи одновременно и возвращает их результаты или брошенные исключения. */
    private static List<Object> runConcurrently(Callable<?>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = java.util.Arrays.stream(tasks)
                    .map(task -> executor.submit(() -> {
                        start.await();
                        try {
                            return (Object) task.call();
                        } catch (Exception ex) {
                            return ex;
                        }
                    }))
                    .toList();
            start.countDown();
            List<Object> results = new java.util.ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions request(User receiver) throws Exception {
        return perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": \"%s\"}".formatted(item.getId())), receiver);
    }

    private ResultActions command(User actor, String transferId, String action) throws Exception {
        return perform(post("/api/transfers/" + transferId + "/" + action), actor);
    }

    private ResultActions itemStatus() throws Exception {
        return perform(get("/api/items/{id}", item.getId()), owner);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(request.with(user(actor.getId().toString()).roles("USER")).with(csrf()));
    }

    private static CurrentUser as(User user) {
        return new CurrentUser(user.getId(), false);
    }
}
