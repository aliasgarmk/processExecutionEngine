package com.unifize.processengine.engine;

import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.User;

public interface TransitionGuard {
    void assertInstanceIsActive(ProcessInstance instance) throws InactiveInstanceException;

    void assertStepIsActive(ProcessInstance instance, String stepId) throws InvalidTransitionException;

    void assertActorIsAuthorised(StepDefinition stepDefinition, ProcessInstance instance, StepState stepState, User actor)
            throws UnauthorisedTransitionException;

    void assertActionIsValid(StepDefinition stepDefinition, StepState stepState, Action action)
            throws InvalidTransitionException;

    /** Guards advanceStep — a step must be IN_PROGRESS before it can be advanced. */
    void assertStepIsInProgress(StepState stepState) throws InvalidTransitionException;
}
