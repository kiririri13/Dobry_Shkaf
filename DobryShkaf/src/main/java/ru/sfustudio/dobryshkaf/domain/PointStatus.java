package ru.sfustudio.dobryshkaf.domain;

public enum PointStatus {
    ACTIVE,
    /** Виден на карте с пометкой о закрытии. */
    TEMPORARILY_CLOSED,
    /** Скрыт из выдачи, но не удалён, чтобы не терять историю. */
    ARCHIVED
}
