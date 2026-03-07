package com.unifize.processengine.engine;

import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;

import java.time.Instant;
import java.util.List;

public interface AuditWriter {
    AuditEntry record(
            String instanceId,
            String stepStateId,
            User actor,
            StepStatus fromStatus,
            StepStatus toStatus,
            ActionType action,
            String reason,
            Instant occurredAt
    );

    AuditEntry recordSystemAction(
            String instanceId,
            String stepStateId,
            StepStatus fromStatus,
            StepStatus toStatus,
            ActionType action,
            String reason,
            Instant occurredAt
    );

    List<AuditEntry> getTrailForInstance(String instanceId);
}
