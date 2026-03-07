package com.unifize.processengine.model;

import java.util.List;

public record AssigneeRule(
        AssigneeRuleType type,
        List<String> userIds,
        String fieldName
) {
    public static AssigneeRule initiator() {
        return new AssigneeRule(AssigneeRuleType.INITIATOR, List.of(), null);
    }

    public static AssigneeRule users(List<String> userIds) {
        return new AssigneeRule(AssigneeRuleType.USER_IDS, List.copyOf(userIds), null);
    }

    public static AssigneeRule fromField(String fieldName) {
        return new AssigneeRule(AssigneeRuleType.FIELD_VALUE_USER_ID, List.of(), fieldName);
    }
}
