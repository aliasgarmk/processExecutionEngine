package com.unifize.processengine.model;

import java.util.List;

public record StepResult(
        String instanceId,
        String completedStepId,
        StepStatus outcomeStatus,
        List<String> nextStepIds,
        List<String> remainingParticipants,
        boolean processCompleted,
        AuditEntry auditEntry
) {
}
