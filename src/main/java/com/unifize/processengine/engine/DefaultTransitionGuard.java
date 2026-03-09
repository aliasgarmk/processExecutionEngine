package com.unifize.processengine.engine;

import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;

import java.util.Objects;

public final class DefaultTransitionGuard implements TransitionGuard {

    @Override
    public void assertInstanceIsActive(ProcessInstance instance) throws InactiveInstanceException {
        if (!instance.isActive()) {
            throw new InactiveInstanceException("Instance is not active: " + instance.instanceId());
        }
    }

    @Override
    public void assertStepIsActive(ProcessInstance instance, String stepId) throws InvalidTransitionException {
        if (!instance.activeStepIds().contains(stepId)) {
            throw new InvalidTransitionException("Step is not active: " + stepId);
        }
    }

    @Override
    public void assertActorIsAuthorised(StepDefinition stepDefinition, ProcessInstance instance,
                                        StepState stepState, User actor) throws UnauthorisedTransitionException {
        if (User.SYSTEM_USER_ID.equals(actor.userId())) {
            return;
        }
        String actorId = actor.userId();
        boolean allowed = switch (stepDefinition.assigneeRule().type()) {
            case INITIATOR -> Objects.equals(instance.initiator().userId(), actorId);
            case USER_IDS -> stepDefinition.assigneeRule().userIds().contains(actorId);
            case FIELD_VALUE_USER_ID ->
                    Objects.equals(String.valueOf(instance.getFieldValue(stepDefinition.assigneeRule().fieldName())), actorId);
        };
        allowed = allowed && Objects.equals(stepState.assignedTo(), actorId);
        if (!allowed) {
            throw new UnauthorisedTransitionException("Actor is not authorised for step: " + stepDefinition.stepId());
        }
    }

    @Override
    public void assertStepIsInProgress(StepState stepState) throws InvalidTransitionException {
        if (stepState.status() != StepStatus.IN_PROGRESS) {
            throw new InvalidTransitionException(
                    "Step must be IN_PROGRESS before it can be advanced; current status: " + stepState.status());
        }
    }

    @Override
    public void assertActionIsValid(StepDefinition stepDefinition, StepState stepState, Action action)
            throws InvalidTransitionException {
        ActionType actionType = action.getType();

        // System-initiated escalation is always valid on an in-progress step.
        if (actionType == ActionType.ESCALATE) {
            return;
        }

        // REJECT requires a non-blank reason.
        if (actionType == ActionType.REJECT
                && (action.getReason() == null || action.getReason().isBlank())) {
            throw new InvalidTransitionException("A reason is required when rejecting a step");
        }

        switch (stepDefinition.stepType()) {
            case APPROVAL, PARALLEL_APPROVAL -> {
                if (actionType != ActionType.APPROVE && actionType != ActionType.REJECT) {
                    throw new InvalidTransitionException(
                            stepDefinition.stepType() + " steps only allow APPROVE or REJECT");
                }
            }
            case REVIEW -> {
                // REVIEW allows approval, rejection (with routing deciding the consequence), or submit.
                if (actionType != ActionType.APPROVE
                        && actionType != ActionType.REJECT
                        && actionType != ActionType.SUBMIT) {
                    throw new InvalidTransitionException("REVIEW steps only allow APPROVE, REJECT, or SUBMIT");
                }
            }
            default -> {
                if (actionType != ActionType.SUBMIT && actionType != ActionType.REOPEN) {
                    throw new InvalidTransitionException("Task-like steps only allow SUBMIT or REOPEN");
                }
            }
        }
    }
}
