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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.support.WeekThreeFixtures;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WeekThreeFixtures.class})
class PasswordResetIntegrationTest {

    private static final String EMAIL = "anna@example.com";
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeekThreeFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        fixtures.clean();
        fixtures.user(EMAIL);
    }

    @Test
    void knownAndUnknownEmailGetTheSameAnswer() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted()).andExpect(content().string(""));
        requestReset("nobody@example.com").andExpect(status().isAccepted()).andExpect(content().string(""));

        verify(mailSender, after(1000).times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void linkWorksOnceAndChangesPassword() throws Exception {
        String token = requestResetToken();

        confirm(token, "new-secret-password").andExpect(status().isOk());

        login("new-secret-password").andExpect(status().isOk());
        login(WeekThreeFixtures.PASSWORD).andExpect(status().isUnauthorized());
        confirm(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        String token = requestResetToken();
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");

        confirm(token, "new-secret-password").andExpect(status().isBadRequest());
        login(WeekThreeFixtures.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void tokenIsStoredOnlyAsHashAndLinkLivesThirtyMinutes() throws Exception {
        String token = requestResetToken();

        String storedHash = jdbcTemplate.queryForObject("select token_hash from password_reset_tokens", String.class);
        Integer minutes = jdbcTemplate.queryForObject(
                "select round(extract(epoch from (expires_at - created_at)) / 60)::int from password_reset_tokens", Integer.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(token);
        assertThat(minutes).isEqualTo(30);
    }

    private String requestResetToken() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(mail.capture());
        Matcher matcher = TOKEN.matcher(mail.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, password)));
    }
}
