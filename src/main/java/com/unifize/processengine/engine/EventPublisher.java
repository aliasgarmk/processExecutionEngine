package com.unifize.processengine.engine;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.User;

public interface EventPublisher {
    void publishProcessStarted(ProcessInstance instance);

    void publishStepTransitioned(StepState stepState, Action action, User actor);

    void publishProcessCompleted(ProcessInstance instance);

    void publishEscalationTriggered(StepState stepState, User escalatedTo);
}
