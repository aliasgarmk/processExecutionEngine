package com.unifize.processengine.model;

import java.time.Duration;
import java.util.List;

public record StepDefinition(
        String stepId,
        StepType stepType,
        AssigneeRule assigneeRule,
        List<FieldSchema> fieldSchemas,
        List<ValidationRule> validationRules,
        QuorumPolicy quorumPolicy,
        EscalationPolicy escalationPolicy,
        Duration scheduledOffset
) {
    public StepDefinition {
        fieldSchemas = List.copyOf(fieldSchemas);
        validationRules = List.copyOf(validationRules);
    }
}
