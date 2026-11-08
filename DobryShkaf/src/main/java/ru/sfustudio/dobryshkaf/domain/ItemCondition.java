package ru.sfustudio.dobryshkaf.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ItemCondition {
    NEW("Новое"),
    LIKE_NEW("Почти новое"),
    USED("Поношенное"),
    NEEDS_CARE("Требует ухода");

    private final String label;
}
