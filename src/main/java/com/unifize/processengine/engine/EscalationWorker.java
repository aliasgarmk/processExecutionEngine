package com.unifize.processengine.engine;

import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.EscalationEvent;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;

import java.util.List;

public final class EscalationWorker {
    private final ProcessEngine processEngine;
    private final StateStore stateStore;

    public EscalationWorker(ProcessEngine processEngine, StateStore stateStore) {
        this.processEngine = processEngine;
        this.stateStore = stateStore;
    }

    public void handleEscalationEvent(EscalationEvent event)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {
        if (!validateEscalationIsStillApplicable(event)) {
            return;
        }
        processEscalation(event);
    }

    public boolean validateEscalationIsStillApplicable(EscalationEvent event) {
        ProcessInstance instance = stateStore.loadInstance(event.instanceId());
        if (!instance.isActive()) {
            return false;
        }
        List<StepState> stepStates = stateStore.loadStepStates(event.instanceId());
        return stepStates.stream()
                .anyMatch(stepState -> stepState.stepStateId().equals(event.stepStateId())
                        && stepState.status() == event.expectedStatus());
    }

    public void processEscalation(EscalationEvent event)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {
        StepState stepState = stateStore.loadStepStates(event.instanceId()).stream()
                .filter(candidate -> candidate.stepStateId().equals(event.stepStateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown step state: " + event.stepStateId()));
        processEngine.advanceStep(
                event.instanceId(),
                stepState.stepId(),
                ActionType.ESCALATE,
                InMemoryAuditWriter.SYSTEM_USER,
                java.util.Map.of()
        );
    }
}
