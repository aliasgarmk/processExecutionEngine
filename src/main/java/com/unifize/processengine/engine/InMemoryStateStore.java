package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DuplicateInstanceException;
import com.unifize.processengine.exception.InstanceNotFoundException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;

import java.util.Comparator;
import java.util.List;

public final class InMemoryStateStore implements StateStore {
    private final InMemoryPersistence persistence;

    public InMemoryStateStore(InMemoryPersistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public void saveInstance(ProcessInstance instance, List<StepState> initialStepStates, List<AuditEntry> auditEntries)
            throws DuplicateInstanceException {
        synchronized (persistence) {
            if (persistence.instances.containsKey(instance.instanceId())) {
                throw new DuplicateInstanceException("Duplicate instance: " + instance.instanceId());
            }
            persistence.instances.put(instance.instanceId(), instance.copy());
            persistence.stepStates(instance.instanceId()).addAll(initialStepStates);
            persistence.auditEntries(instance.instanceId()).addAll(auditEntries);
        }
    }

    @Override
    public void updateInstance(ProcessInstance instance, List<StepState> stepStates, List<AuditEntry> auditEntries)
            throws OptimisticLockException {
        synchronized (persistence) {
            ProcessInstance stored = persistence.instances.get(instance.instanceId());
            if (stored == null) {
                throw new InstanceNotFoundException("Instance not found: " + instance.instanceId());
            }
            if (stored.version() != instance.version()) {
                throw new OptimisticLockException("Version conflict for instance: " + instance.instanceId());
            }

            ProcessInstance updated = instance.copy();
            updated.incrementVersion();
            persistence.instances.put(updated.instanceId(), updated);

            List<StepState> allStates = persistence.stepStates(updated.instanceId());
            for (StepState candidate : stepStates) {
                int existingIndex = findStateIndex(allStates, candidate.stepStateId());
                if (existingIndex >= 0) {
                    allStates.set(existingIndex, candidate);
                } else {
                    allStates.add(candidate);
                }
            }
            persistence.auditEntries(updated.instanceId()).addAll(auditEntries);
        }
    }

    @Override
    public ProcessInstance loadInstance(String instanceId) throws InstanceNotFoundException {
        ProcessInstance stored = persistence.instances.get(instanceId);
        if (stored == null) {
            throw new InstanceNotFoundException("Instance not found: " + instanceId);
        }
        return stored.copy();
    }

    @Override
    public List<StepState> loadStepStates(String instanceId) {
        return persistence.stepStates(instanceId).stream()
                .sorted(Comparator.comparingLong(StepState::sequenceNumber))
                .toList();
    }

    @Override
    public List<StepState> loadStepStatesForStep(String instanceId, String stepId) {
        return persistence.stepStates(instanceId).stream()
                .filter(state -> state.stepId().equals(stepId))
                .sorted(Comparator.comparingLong(StepState::sequenceNumber))
                .toList();
    }

    private int findStateIndex(List<StepState> states, String stepStateId) {
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).stepStateId().equals(stepStateId)) {
                return index;
            }
        }
        return -1;
    }
}
