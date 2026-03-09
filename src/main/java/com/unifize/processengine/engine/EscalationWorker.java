package com.unifize.processengine.engine;

import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.EscalationEvent;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.User;

import java.util.List;

public final class EscalationWorker {
    private final ProcessEngine processEngine;
    private final StateStore stateStore;

    public EscalationWorker(ProcessEngine processEngine, StateStore stateStore) {
        this.processEngine = processEngine;
        this.stateStore = stateStore;
    }

    /**
     * Entry point for the message consumer. Idempotent — duplicate deliveries are silently discarded.
     */
    public void handleEscalationEvent(EscalationEvent event)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {
        if (!validateEscalationIsStillApplicable(event)) {
            return;
        }
        processEscalation(event);
    }

    private boolean validateEscalationIsStillApplicable(EscalationEvent event) {
        ProcessInstance instance = stateStore.loadInstance(event.instanceId());
        if (!instance.isActive()) {
            return false;
        }
        List<StepState> stepStates = stateStore.loadStepStates(event.instanceId());
        return stepStates.stream()
                .anyMatch(s -> s.stepStateId().equals(event.stepStateId())
                        && s.status() == event.expectedStatus());
    }

    private void processEscalation(EscalationEvent event)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {
        StepState stepState = stateStore.loadStepStates(event.instanceId()).stream()
                .filter(s -> s.stepStateId().equals(event.stepStateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown step state: " + event.stepStateId()));
        processEngine.advanceStep(
                event.instanceId(),
                stepState.stepId(),
                Action.escalate(event.reason()),
                User.SYSTEM
        );
    }
}
