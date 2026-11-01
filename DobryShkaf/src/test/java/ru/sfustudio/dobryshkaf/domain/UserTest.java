package ru.sfustudio.dobryshkaf.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

    private static User user() {
        return new User("anna@example.com", "hash", "Анна", UserRole.USER);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankRequiredText(String blank) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User(blank, "hash", "Анна", UserRole.USER));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User("anna@example.com", blank, "Анна", UserRole.USER));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User("anna@example.com", "hash", blank, UserRole.USER));
    }

    @Test
    void rejectsNullRole() {
        assertThatNullPointerException()
                .isThrownBy(() -> new User("anna@example.com", "hash", "Анна", null));
    }

    @Test
    void newUserHasNoRating() {
        assertThat(user().getRating()).isNull();
    }

    @Test
    void roundsRatingToTwoDecimalPlaces() {
        User user = user();

        user.updateRating(new BigDecimal("4.666"));

        assertThat(user.getRating()).isEqualTo(new BigDecimal("4.67"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.99", "5.01"})
    void rejectsRatingOutOfRange(String rating) {
        BigDecimal outOfRange = new BigDecimal(rating);

        assertThatIllegalArgumentException().isThrownBy(() -> user().updateRating(outOfRange));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "5"})
    void acceptsRatingBounds(String rating) {
        User user = user();

        user.updateRating(new BigDecimal(rating));

        assertThat(user.getRating()).isEqualByComparingTo(rating);
    }

    @Test
    void unsavedUsersAreNotEqual() {
        assertThat(user()).isNotEqualTo(user());
    }
}
