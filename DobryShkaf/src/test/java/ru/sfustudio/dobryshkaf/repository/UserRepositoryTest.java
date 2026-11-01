package ru.sfustudio.dobryshkaf.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void assignsIdAndTimestampsOnSave() {
        User saved = userRepository.saveAndFlush(user("anna@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsByEmailIgnoringCase() {
        User saved = userRepository.saveAndFlush(user("Anna@Example.com"));

        assertThat(userRepository.findByEmailIgnoreCase("anna@example.COM")).contains(saved);
        assertThat(userRepository.existsByEmailIgnoreCase("ANNA@example.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("other@example.com")).isFalse();
    }

    @Test
    void rejectsEmailThatDiffersOnlyByCase() {
        userRepository.saveAndFlush(user("anna@example.com"));
        User duplicate = user("ANNA@example.com");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> userRepository.saveAndFlush(duplicate));
    }

    private static User user(String email) {
        return new User(email, "hash", "Анна", UserRole.USER);
    }
}
