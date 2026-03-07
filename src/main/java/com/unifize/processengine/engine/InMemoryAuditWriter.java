package com.unifize.processengine.engine;

import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;
import com.unifize.processengine.support.SequenceGenerator;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class InMemoryAuditWriter implements AuditWriter {
    public static final User SYSTEM_USER = new User("SYSTEM", "System");

    private final SequenceGenerator sequenceGenerator;
    private final InMemoryPersistence persistence;

    public InMemoryAuditWriter(SequenceGenerator sequenceGenerator, InMemoryPersistence persistence) {
        this.sequenceGenerator = sequenceGenerator;
        this.persistence = persistence;
    }

    @Override
    public AuditEntry record(
            String instanceId,
            String stepStateId,
            User actor,
            StepStatus fromStatus,
            StepStatus toStatus,
            ActionType action,
            String reason,
            Instant occurredAt
    ) {
        return new AuditEntry(
                sequenceGenerator.next(),
                instanceId,
                stepStateId,
                actor.userId(),
                occurredAt,
                fromStatus,
                toStatus,
                action,
                reason
        );
    }

    @Override
    public AuditEntry recordSystemAction(
            String instanceId,
            String stepStateId,
            StepStatus fromStatus,
            StepStatus toStatus,
            ActionType action,
            String reason,
            Instant occurredAt
    ) {
        return record(instanceId, stepStateId, SYSTEM_USER, fromStatus, toStatus, action, reason, occurredAt);
    }

    @Override
    public List<AuditEntry> getTrailForInstance(String instanceId) {
        return persistence.auditEntries(instanceId).stream()
                .sorted(Comparator.comparingLong(AuditEntry::sequenceNumber))
                .toList();
    }
}
