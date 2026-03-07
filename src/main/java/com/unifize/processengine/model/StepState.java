package com.unifize.processengine.model;

import java.time.Instant;

public record StepState(
        String stepStateId,
        String instanceId,
        String stepId,
        String assignedTo,
        StepStatus status,
        Instant startedAt,
        Instant completedAt,
        String participantId,
        long sequenceNumber
) {
    public StepState withStatus(StepStatus nextStatus, Instant timestamp) {
        return new StepState(
                stepStateId,
                instanceId,
                stepId,
                assignedTo,
                nextStatus,
                startedAt,
                isTerminal(nextStatus) ? timestamp : completedAt,
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
