package com.unifize.processengine.api.dto.response;

import com.unifize.processengine.model.AuditEntry;

import java.time.Instant;

public record AuditEntryResponse(
        long sequenceNumber,
        String instanceId,
        String stepStateId,
        String actorUserId,
        Instant timestamp,
        String fromStatus,
        String toStatus,
        String actionType,
        String reason
) {
    public static AuditEntryResponse from(AuditEntry e) {
        return new AuditEntryResponse(
                e.sequenceNumber(),
                e.instanceId(),
                e.stepStateId(),
                e.actorUserId(),
                e.timestamp(),
                e.fromStatus() != null ? e.fromStatus().name() : null,
                e.toStatus().name(),
                e.actionType().name(),
                e.reason()
        );
    }
}
