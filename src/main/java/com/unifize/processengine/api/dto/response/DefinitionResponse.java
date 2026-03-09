package com.unifize.processengine.api.dto.response;

import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.StepDefinition;

import java.time.Instant;
import java.util.List;

public record DefinitionResponse(
        String definitionId,
        int version,
        String name,
        List<StepSummary> steps,
        Instant publishedAt,
        String publishedBy
) {
    public static DefinitionResponse from(ProcessDefinition d) {
        return new DefinitionResponse(
                d.definitionId(),
                d.version(),
                d.name(),
                d.steps().stream().map(StepSummary::from).toList(),
                d.publishedAt(),
                d.publishedBy()
        );
    }

    public record StepSummary(String stepId, String stepType, String assigneeRuleType) {
        static StepSummary from(StepDefinition s) {
            return new StepSummary(s.stepId(), s.stepType().name(), s.assigneeRule().type().name());
        }
    }
}
