package com.unifize.processengine.engine;

import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.User;

import java.util.List;

public interface EventPublisher {
    void publishProcessStarted(ProcessInstance instance);

    void publishStepTransitioned(StepState stepState, ActionType action, User actor);

    void publishProcessCompleted(ProcessInstance instance);

    void publishEscalationTriggered(StepState stepState, User escalatedTo);

    List<String> publishedEvents();
}
