package ru.sfustudio.dobryshkaf.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.dto.SendMessageRequest;
import ru.sfustudio.dobryshkaf.service.ChatService;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.mail-from=notifier@example.com")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class ChatMailIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    @Autowired
    private ChatService chats;

    @Autowired
    private TransactionTemplate transactions;

    @MockitoBean
    private JavaMailSender mailSender;

    private User owner;
    private User requester;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        owner = fixtures.user("owner@example.com");
        requester = fixtures.user("requester@example.com");
        UUID itemId = fixtures.publishedItem(owner).getId();
        chatId = chats.open(itemId, new CurrentUser(requester.getId(), false)).body().id();
    }

    @Test
    void notifiesListingOwnerOncePerNewIncomingMessage() throws Exception {
        UUID requestId = UUID.randomUUID();

        send(requester, requestId).andExpect(status().isCreated());
        send(requester, requestId).andExpect(status().isOk());
        send(owner, UUID.randomUUID()).andExpect(status().isCreated());
        send(requester, UUID.randomUUID()).andExpect(status().isCreated());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000).times(2)).send(mail.capture());
        verify(mailSender, after(500).times(2)).send(any(SimpleMailMessage.class));
        assertThat(mail.getAllValues()).allSatisfy(message -> {
            assertThat(message.getTo()).containsExactly(owner.getEmail());
            assertThat(message.getFrom()).isEqualTo("notifier@example.com");
            assertThat(message.getSubject()).contains("Новое сообщение");
            assertThat(message.getText()).contains("requester", "Книга").doesNotContain("Личный текст");
        });
    }

    @Test
    void rolledBackMessageDoesNotSendEmail() {
        CurrentUser sender = new CurrentUser(requester.getId(), false);
        transactions.executeWithoutResult(transaction -> {
            assertThat(chats.send(chatId, new SendMessageRequest("Личный текст", UUID.randomUUID()), sender).created())
                    .isTrue();
            transaction.setRollbackOnly();
        });

        assertThat(chats.messages(chatId, sender, null, null, null).messages()).isEmpty();
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));
    }

    private ResultActions send(User sender, UUID requestId) throws Exception {
        return mvc.perform(post("/api/chats/{id}/messages", chatId)
                .with(user(sender.getId().toString()).roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"Личный текст","requestId":"%s"}""".formatted(requestId)));
    }
}
