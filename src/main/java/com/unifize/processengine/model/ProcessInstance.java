package com.unifize.processengine.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents one running execution of a process definition.
 * Immutable — all state transitions return a new instance.
 * Mutations must go through the engine, never through direct field access.
 */
public record ProcessInstance(
        String instanceId,
        String definitionId,
        int definitionVersion,
        User initiator,
        Map<String, Object> fieldValues,
        Set<String> activeStepIds,
        InstanceStatus status,
        long version,
        Instant createdAt
) {
    public ProcessInstance {
        fieldValues = Map.copyOf(fieldValues);
        activeStepIds = Set.copyOf(activeStepIds);
    }

    public Object getFieldValue(String fieldName) {
        return fieldValues.get(fieldName);
    }

    public boolean isActive() {
        return status == InstanceStatus.ACTIVE;
    }

    public ProcessInstance withFieldValues(Map<String, Object> additionalFields) {
        Map<String, Object> merged = new HashMap<>(fieldValues);
        merged.putAll(additionalFields);
        return new ProcessInstance(instanceId, definitionId, definitionVersion, initiator,
                merged, activeStepIds, status, version, createdAt);
    }

    public ProcessInstance withActiveStepIds(Set<String> newActiveStepIds) {
        return new ProcessInstance(instanceId, definitionId, definitionVersion, initiator,
                fieldValues, newActiveStepIds, status, version, createdAt);
    }

    public ProcessInstance withStatus(InstanceStatus newStatus) {
        return new ProcessInstance(instanceId, definitionId, definitionVersion, initiator,
                fieldValues, activeStepIds, newStatus, version, createdAt);
    }

    public ProcessInstance withIncrementedVersion() {
        return new ProcessInstance(instanceId, definitionId, definitionVersion, initiator,
                fieldValues, activeStepIds, status, version + 1, createdAt);
    }

    /** Returns a COMPLETED copy if there are no remaining active steps; otherwise returns this. */
    public ProcessInstance completeIfNoActiveSteps() {
        if (activeStepIds.isEmpty()) {
            return withStatus(InstanceStatus.COMPLETED);
        }
        return this;
    }
}
