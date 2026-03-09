package com.unifize.processengine.engine;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;

import java.time.Instant;
import java.util.List;

public interface AuditWriter {

    /**
     * Constructs, persists, and returns a new AuditEntry.
     * This is the only write path to the audit log — no other class may insert audit records.
     * The server-assigned timestamp is applied internally; callers must not supply one.
     */
    AuditEntry record(
            String instanceId,
            String stepStateId,
            User actor,
            StepStatus fromStatus,
            StepStatus toStatus,
            Action action
    );

    /**
     * Convenience overload for system-initiated events (escalations, scheduled triggers).
     * Substitutes {@link User#SYSTEM} as the actor. Prevents accidental null-actor calls.
     */
    AuditEntry recordSystemAction(
            String instanceId,
            String stepStateId,
            StepStatus fromStatus,
            StepStatus toStatus,
            Action action
    );

    /**
     * Returns all AuditEntries for the given instanceId ordered by sequenceNumber ascending.
     */
    List<AuditEntry> getTrailForInstance(String instanceId);

    /**
     * Returns all AuditEntries with timestamps in the given range, across all instances.
     * Used for compliance reports.
     */
    List<AuditEntry> getEntriesByDateRange(Instant from, Instant to);
}
