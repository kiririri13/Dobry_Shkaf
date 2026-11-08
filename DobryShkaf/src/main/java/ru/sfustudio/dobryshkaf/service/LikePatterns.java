package ru.sfustudio.dobryshkaf.service;

import java.util.Locale;

/** Шаблоны для поиска подстроки через SQL LIKE. */
final class LikePatterns {

    static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** Без экранирования запрос «%» нашёл бы все записи. */
    static String containsIgnoringCase(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
