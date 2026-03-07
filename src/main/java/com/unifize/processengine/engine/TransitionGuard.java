package com.unifize.processengine.engine;

import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.User;

public interface TransitionGuard {
    void assertInstanceIsActive(ProcessInstance instance) throws InactiveInstanceException;

    void assertStepIsActive(ProcessInstance instance, String stepId) throws InvalidTransitionException;

    void assertActorIsAuthorised(StepDefinition stepDefinition, ProcessInstance instance, StepState stepState, User actor)
            throws UnauthorisedTransitionException;

    void assertActionAllowed(StepDefinition stepDefinition, StepState stepState, ActionType action)
            throws InvalidTransitionException;
}
