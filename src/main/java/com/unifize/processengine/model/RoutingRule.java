package com.unifize.processengine.model;

import java.util.List;

public record RoutingRule(
        String sourceStepId,
        String condition,
        boolean defaultRoute,
        List<String> targetStepIds
) {
    public RoutingRule {
        targetStepIds = List.copyOf(targetStepIds);
    }
}
