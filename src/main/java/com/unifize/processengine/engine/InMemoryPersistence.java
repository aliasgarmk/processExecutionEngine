package com.unifize.processengine.engine;

import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared in-memory backing store. All mutable state is private.
 * Callers must synchronize on this object when performing compound read-modify-write operations.
 */
final class InMemoryPersistence {
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, List<StepState>> stepStatesByInstance = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEntry>> auditEntriesByInstance = new ConcurrentHashMap<>();

    boolean containsInstance(String instanceId) {
        return instances.containsKey(instanceId);
    }

    void putInstance(String instanceId, ProcessInstance instance) {
        instances.put(instanceId, instance);
    }

    ProcessInstance getInstance(String instanceId) {
        return instances.get(instanceId);
    }

    Collection<ProcessInstance> allInstances() {
        return instances.values();
    }

    /** Returns the step-state list for an instance, creating it if absent. */
    List<StepState> stepStates(String instanceId) {
        return stepStatesByInstance.computeIfAbsent(instanceId, ignored -> new CopyOnWriteArrayList<>());
    }

    /** Returns the audit-entry list for an instance, creating it if absent. */
    List<AuditEntry> auditEntries(String instanceId) {
        return auditEntriesByInstance.computeIfAbsent(instanceId, ignored -> new CopyOnWriteArrayList<>());
    }

    Collection<List<AuditEntry>> allAuditEntries() {
        return auditEntriesByInstance.values();
    }
}
