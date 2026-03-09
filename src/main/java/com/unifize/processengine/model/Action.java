package com.unifize.processengine.model;

import java.util.Map;

/**
 * Encapsulates what an actor is doing and what they submitted.
 * Use static factory methods — never the constructor directly.
 */
public final class Action {

    private final ActionType type;
    private final Map<String, Object> fields;
    private final String reason;

    private Action(ActionType type, Map<String, Object> fields, String reason) {
        this.type = type;
        this.fields = Map.copyOf(fields);
        this.reason = reason;
    }

    public static Action approve() {
        return new Action(ActionType.APPROVE, Map.of(), null);
    }

    public static Action reject(String reason) {
        return new Action(ActionType.REJECT, Map.of(), reason);
    }

    public static Action submit(Map<String, Object> fields) {
        return new Action(ActionType.SUBMIT, fields, null);
    }

    public static Action escalate(String reason) {
        return new Action(ActionType.ESCALATE, Map.of(), reason);
    }

    public static Action reopen(String reason) {
        return new Action(ActionType.REOPEN, Map.of(), reason);
    }

    public static Action reassign(String reason) {
        return new Action(ActionType.REASSIGN, Map.of(), reason);
    }

    public static Action open() {
        return new Action(ActionType.OPEN, Map.of(), null);
    }

    public ActionType getType() {
        return type;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Maps this action to the StepStatus it produces on the target step.
     * Centralises the ActionType → StepStatus domain rule so ProcessEngine
     * does not need to own it.
     */
    public StepStatus resolvedStatus() {
        return switch (type) {
            case APPROVE, SUBMIT, REOPEN -> StepStatus.COMPLETED;
            case REJECT -> StepStatus.REJECTED;
            case ESCALATE -> StepStatus.ESCALATED;
            case REASSIGN -> StepStatus.IN_PROGRESS;
            case OPEN -> StepStatus.IN_PROGRESS;
        };
    }
}
