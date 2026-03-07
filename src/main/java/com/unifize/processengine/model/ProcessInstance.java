package com.unifize.processengine.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProcessInstance {
    private final String instanceId;
    private final String definitionId;
    private final int definitionVersion;
    private final User initiator;
    private final Instant createdAt;
    private final Map<String, Object> fieldValues;
    private final Set<String> activeStepIds;
    private InstanceStatus status;
    private long version;

    public ProcessInstance(
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
        this.instanceId = Objects.requireNonNull(instanceId);
        this.definitionId = Objects.requireNonNull(definitionId);
        this.definitionVersion = definitionVersion;
        this.initiator = Objects.requireNonNull(initiator);
        this.fieldValues = new HashMap<>(fieldValues);
        this.activeStepIds = new HashSet<>(activeStepIds);
        this.status = Objects.requireNonNull(status);
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public ProcessInstance copy() {
        return new ProcessInstance(instanceId, definitionId, definitionVersion, initiator, fieldValues, activeStepIds, status, version, createdAt);
    }

    public String instanceId() {
        return instanceId;
    }

    public String definitionId() {
        return definitionId;
    }

    public int definitionVersion() {
        return definitionVersion;
    }

    public User initiator() {
        return initiator;
    }

    public Map<String, Object> fieldValues() {
        return fieldValues;
    }

    public Set<String> activeStepIds() {
        return activeStepIds;
    }

    public InstanceStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Object getFieldValue(String fieldName) {
        return fieldValues.get(fieldName);
    }

    public boolean isActive() {
        return status == InstanceStatus.ACTIVE;
    }

    public void putFieldValues(Map<String, Object> fields) {
        fieldValues.putAll(fields);
    }

    public void setActiveStepIds(Set<String> stepIds) {
        activeStepIds.clear();
        activeStepIds.addAll(stepIds);
    }

    public void completeIfNoActiveSteps() {
        if (activeStepIds.isEmpty()) {
            status = InstanceStatus.COMPLETED;
        }
    }

    public void incrementVersion() {
        version++;
    }
}
