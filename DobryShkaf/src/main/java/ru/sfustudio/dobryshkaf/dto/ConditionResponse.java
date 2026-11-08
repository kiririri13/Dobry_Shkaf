package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.ItemCondition;

public record ConditionResponse(ItemCondition code, String label) {

    public static ConditionResponse from(ItemCondition condition) {
        return new ConditionResponse(condition, condition.getLabel());
    }
}
