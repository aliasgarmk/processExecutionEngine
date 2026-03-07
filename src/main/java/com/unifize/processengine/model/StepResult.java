package com.unifize.processengine.model;

import java.util.List;

public record StepResult(
        String instanceId,
        String stepId,
        StepStatus resultingStatus,
        List<String> nextStepIds,
        List<String> remainingParticipants,
        boolean processCompleted
) {
}
