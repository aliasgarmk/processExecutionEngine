package com.unifize.processengine.api.dto.response;

import com.unifize.processengine.model.StepResult;

import java.util.List;

public record StepResultResponse(
        String instanceId,
        String completedStepId,
        String outcomeStatus,
        List<String> nextStepIds,
        List<String> remainingParticipants,
        boolean processCompleted,
        AuditEntryResponse auditEntry
) {
    public static StepResultResponse from(StepResult r) {
        return new StepResultResponse(
                r.instanceId(),
                r.completedStepId(),
                r.outcomeStatus().name(),
                r.nextStepIds(),
                r.remainingParticipants(),
                r.processCompleted(),
                AuditEntryResponse.from(r.auditEntry())
        );
    }
}
