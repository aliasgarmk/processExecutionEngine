package com.unifize.processengine.model;

import java.time.Instant;
import java.util.List;

public record ProcessDefinition(
        String definitionId,
        int version,
        String name,
        List<StepDefinition> steps,
        List<RoutingRule> routingRules,
        Instant publishedAt,
        String publishedBy
) {
    public ProcessDefinition {
        steps = List.copyOf(steps);
        routingRules = List.copyOf(routingRules);
    }
}
