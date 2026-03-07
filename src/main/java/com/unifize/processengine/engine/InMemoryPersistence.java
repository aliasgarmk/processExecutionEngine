package com.unifize.processengine.engine;

import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryPersistence {
    final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    final Map<String, List<StepState>> stepStatesByInstance = new ConcurrentHashMap<>();
    final Map<String, List<AuditEntry>> auditEntriesByInstance = new ConcurrentHashMap<>();

    List<StepState> stepStates(String instanceId) {
        return stepStatesByInstance.computeIfAbsent(instanceId, ignored -> new ArrayList<>());
    }

    List<AuditEntry> auditEntries(String instanceId) {
        return auditEntriesByInstance.computeIfAbsent(instanceId, ignored -> new ArrayList<>());
    }
}
