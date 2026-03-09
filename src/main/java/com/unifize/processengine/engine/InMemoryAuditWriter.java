package com.unifize.processengine.engine;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;
import com.unifize.processengine.support.SequenceGenerator;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class InMemoryAuditWriter implements AuditWriter {

    private final SequenceGenerator sequenceGenerator;
    private final ClockProvider clockProvider;
    private final InMemoryPersistence persistence;

    public InMemoryAuditWriter(SequenceGenerator sequenceGenerator, ClockProvider clockProvider,
                               InMemoryPersistence persistence) {
        this.sequenceGenerator = sequenceGenerator;
        this.clockProvider = clockProvider;
        this.persistence = persistence;
    }

    @Override
    public AuditEntry record(
            String instanceId,
            String stepStateId,
            User actor,
            StepStatus fromStatus,
            StepStatus toStatus,
            Action action
    ) {
        AuditEntry entry = new AuditEntry(
                sequenceGenerator.next(),
                instanceId,
                stepStateId,
                actor.userId(),
                clockProvider.now(),
                fromStatus,
                toStatus,
                action.getType(),
                action.getReason()
        );
        persistence.auditEntries(instanceId).add(entry);
        return entry;
    }

    @Override
    public AuditEntry recordSystemAction(
            String instanceId,
            String stepStateId,
            StepStatus fromStatus,
            StepStatus toStatus,
            Action action
    ) {
        return record(instanceId, stepStateId, User.SYSTEM, fromStatus, toStatus, action);
    }

    @Override
    public List<AuditEntry> getTrailForInstance(String instanceId) {
        return persistence.auditEntries(instanceId).stream()
                .sorted(Comparator.comparingLong(AuditEntry::sequenceNumber))
                .toList();
    }

    @Override
    public List<AuditEntry> getEntriesByDateRange(Instant from, Instant to) {
        return persistence.allAuditEntries().stream()
                .flatMap(List::stream)
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .sorted(Comparator.comparingLong(AuditEntry::sequenceNumber))
                .collect(Collectors.toList());
    }
}
