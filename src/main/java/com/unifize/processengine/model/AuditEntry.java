package com.unifize.processengine.model;

import java.time.Instant;

public record AuditEntry(
        long sequenceNumber,
        String instanceId,
        String stepStateId,
        String actorUserId,
        Instant timestamp,
        StepStatus fromStatus,
        StepStatus toStatus,
        ActionType actionType,
        String reason
) {
}
