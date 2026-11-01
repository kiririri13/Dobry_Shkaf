package ru.sfustudio.dobryshkaf.service;

import java.util.Optional;
import java.util.regex.Pattern;

/** Приводит телефон к формату E.164, в котором он хранится в БД. */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s()-]");
    private static final Pattern RUSSIAN_TRUNK_FORMAT = Pattern.compile("8\\d{10}");
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{10,14}");

    private PhoneNumbers() {
    }

    public static Optional<String> normalize(String phone) {
        String digits = SEPARATORS.matcher(phone).replaceAll("");
        if (RUSSIAN_TRUNK_FORMAT.matcher(digits).matches()) {
            digits = "+7" + digits.substring(1);
        }
        return E164.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }
}
