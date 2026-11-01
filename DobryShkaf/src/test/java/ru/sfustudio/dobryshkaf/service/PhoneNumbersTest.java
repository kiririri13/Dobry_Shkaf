package ru.sfustudio.dobryshkaf.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"+79991234567", "+7 (999) 123-45-67", "89991234567", "8 999 123 45 67"})
    void normalizesToE164(String phone) {
        assertThat(PhoneNumbers.normalize(phone)).contains("+79991234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "9991234567", "+7999", "+7999123456789012", "abc", "+7999abc4567"})
    void rejectsInvalidNumbers(String phone) {
        assertThat(PhoneNumbers.normalize(phone)).isEmpty();
    }
}
