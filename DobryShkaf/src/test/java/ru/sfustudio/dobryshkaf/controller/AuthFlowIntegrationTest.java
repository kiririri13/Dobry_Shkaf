package ru.sfustudio.dobryshkaf.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.repository.EmailConfirmationTokenRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String EMAIL = "anna@example.com";
    private static final String PASSWORD = "secret-password";
    private static final int MAIL_TIMEOUT_MILLIS = 5000;
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationSendsConfirmationLinkToEmail() throws Exception {
        register(EMAIL, "8 (999) 123-45-67", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.phone").value("+79991234567"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailConfirmed").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        SimpleMailMessage mail = sentMails(1).getValue();
        assertThat(mail.getTo()).containsExactly(EMAIL);
        assertThat(mail.getText()).contains("http://localhost:8080/api/auth/confirm?token=");
    }

    @Test
    void storesOnlyPasswordHash() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());

        String hash = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void cannotLoginBeforeConfirmation() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_CONFIRMED"));
    }

    @Test
    void wrongPasswordDoesNotRevealUnconfirmedAccount() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());

        login(EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logsInByEmailAfterConfirmation() throws Exception {
        registerAndConfirm(EMAIL, null);

        login(" ANNA@example.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.emailConfirmed").value(true));
    }

    @Test
    void logsInByPhoneInAnyFormat() throws Exception {
        registerAndConfirm(EMAIL, "+79991234567");

        login("8 999 123-45-67", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void sessionKeepsUserLoggedInUntilLogout() throws Exception {
        registerAndConfirm(EMAIL, null);
        MockHttpSession session = (MockHttpSession) login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void roleChangeRequiresNewLoginBeforeUsingModerationQueue() throws Exception {
        registerAndConfirm(EMAIL, null);
        MockHttpSession userSession = (MockHttpSession) login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        jdbcTemplate.update("update users set role = 'MODERATOR' where email = ?", EMAIL);
        mockMvc.perform(get("/api/admin/items").session(userSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").session(userSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(userSession.isInvalid()).isTrue();

        MockHttpSession moderatorSession = (MockHttpSession) login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/items").param("status", "PENDING").session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        registerAndConfirm(EMAIL, null);
        MockHttpSession session = (MockHttpSession) login(EMAIL, PASSWORD)
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserGetsSameErrorAsWrongPassword() throws Exception {
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("not-a-phone", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void confirmationLinkIsSingleUse() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());
        String token = lastToken(1);

        confirm(token).andExpect(status().isOk());

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIRMATION_TOKEN"));
    }

    @Test
    void rejectsUnknownToken() throws Exception {
        confirm("unknown").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());
        String token = lastToken(1);
        jdbcTemplate.update("UPDATE email_confirmation_tokens SET expires_at = now() - interval '1 minute'");

        confirm(token).andExpect(status().isBadRequest());

        login(EMAIL, PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void resendReplacesPreviousLink() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());
        String oldToken = lastToken(1);

        resend(EMAIL).andExpect(status().isAccepted());
        String newToken = lastToken(2);

        confirm(oldToken).andExpect(status().isBadRequest());
        confirm(newToken).andExpect(status().isOk());
    }

    @Test
    void resendDoesNotRevealWhetherUserExists() throws Exception {
        resend("nobody@example.com").andExpect(status().isAccepted());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register(EMAIL, null, PASSWORD).andExpect(status().isCreated());

        register("ANNA@example.com", null, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void rejectsDuplicatePhoneInAnotherFormat() throws Exception {
        register(EMAIL, "+79991234567", PASSWORD).andExpect(status().isCreated());

        register("other@example.com", "8 999 123-45-67", PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void validatesRegistrationFields() throws Exception {
        register("not-an-email", "123", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.phoneValid").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimit() throws Exception {
        // 40 кириллических символов занимают 80 байт
        register(EMAIL, null, "пароль".repeat(7).substring(0, 40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.passwordWithinLimit").exists());
    }

    private void registerAndConfirm(String email, String phone) throws Exception {
        register(email, phone, PASSWORD).andExpect(status().isCreated());
        confirm(lastToken(1)).andExpect(status().isOk());
    }

    private ResultActions register(String email, String phone, String password) throws Exception {
        String phoneJson = phone == null ? "null" : "\"%s\"".formatted(phone);
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "phone": %s, "name": "Анна", "password": "%s"}
                        """.formatted(email, phoneJson, password)));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login": "%s", "password": "%s"}
                        """.formatted(login, password)));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/confirm").param("token", token));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private ArgumentCaptor<SimpleMailMessage> sentMails(int expected) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(MAIL_TIMEOUT_MILLIS).times(expected)).send(captor.capture());
        return captor;
    }

    private String lastToken(int expectedMails) {
        Matcher matcher = TOKEN.matcher(sentMails(expectedMails).getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
