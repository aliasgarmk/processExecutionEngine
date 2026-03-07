package com.unifize.processengine.engine;

import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.AssigneeRuleType;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.StepType;
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
    public void assertActorIsAuthorised(StepDefinition stepDefinition, ProcessInstance instance, StepState stepState, User actor)
            throws UnauthorisedTransitionException {
        String actorId = actor.userId();
        if ("SYSTEM".equals(actorId)) {
            return;
        }
        boolean allowed = switch (stepDefinition.assigneeRule().type()) {
            case INITIATOR -> Objects.equals(instance.initiator().userId(), actorId);
            case USER_IDS -> stepDefinition.assigneeRule().userIds().contains(actorId);
            case FIELD_VALUE_USER_ID -> Objects.equals(String.valueOf(instance.getFieldValue(stepDefinition.assigneeRule().fieldName())), actorId);
        };
        allowed = allowed && Objects.equals(stepState.assignedTo(), actorId);
        if (!allowed) {
            throw new UnauthorisedTransitionException("Actor is not authorised for step: " + stepDefinition.stepId());
        }
    }

    @Override
    public void assertActionAllowed(StepDefinition stepDefinition, StepState stepState, ActionType action)
            throws InvalidTransitionException {
        if (stepState.status() != StepStatus.IN_PROGRESS) {
            throw new InvalidTransitionException("Only in-progress steps can be advanced");
        }

        if (action == ActionType.ESCALATE) {
            return;
        }

        if (stepDefinition.stepType() == StepType.APPROVAL || stepDefinition.stepType() == StepType.PARALLEL_APPROVAL) {
            if (action != ActionType.APPROVE && action != ActionType.REJECT) {
                throw new InvalidTransitionException("Approval steps only allow APPROVE or REJECT");
            }
            return;
        }

        if (action != ActionType.SUBMIT && action != ActionType.REOPEN) {
            throw new InvalidTransitionException("Task-like steps only allow SUBMIT or REOPEN");
        }
    }
}
