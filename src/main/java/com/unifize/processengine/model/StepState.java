package com.unifize.processengine.model;

import java.time.Instant;

public record StepState(
        String stepStateId,
        String instanceId,
        String stepId,
        String assignedTo,
        StepStatus status,
        Instant createdAt,      // set when PENDING step is activated
        Instant openedAt,       // set when step transitions PENDING → IN_PROGRESS
        Instant completedAt,    // set when step reaches a terminal status
        String participantId,
        long sequenceNumber
) {
    public StepState withStatus(StepStatus nextStatus, Instant timestamp) {
        Instant newOpenedAt = (nextStatus == StepStatus.IN_PROGRESS && openedAt == null)
                ? timestamp : openedAt;
        Instant newCompletedAt = isTerminal(nextStatus) ? timestamp : completedAt;
        return new StepState(
                stepStateId, instanceId, stepId, assignedTo,
                nextStatus,
                createdAt,
                newOpenedAt,
                newCompletedAt,
                participantId,
                sequenceNumber
        );
    }

    private static boolean isTerminal(StepStatus status) {
        return switch (status) {
            case COMPLETED, REJECTED, ESCALATED, SKIPPED -> true;
            default -> false;
        };
    }
}
