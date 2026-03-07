package com.unifize.processengine.engine;

import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryEventPublisher implements EventPublisher {
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishProcessStarted(ProcessInstance instance) {
        events.add("PROCESS_STARTED:" + instance.instanceId());
    }

    @Override
    public void publishStepTransitioned(StepState stepState, ActionType action, User actor) {
        events.add("STEP_TRANSITIONED:" + stepState.instanceId() + ":" + stepState.stepId() + ":" + action + ":" + actor.userId());
    }

    @Override
    public void publishProcessCompleted(ProcessInstance instance) {
        events.add("PROCESS_COMPLETED:" + instance.instanceId());
    }

    @Override
    public void publishEscalationTriggered(StepState stepState, User escalatedTo) {
        events.add("ESCALATION_TRIGGERED:" + stepState.instanceId() + ":" + stepState.stepId() + ":" + escalatedTo.userId());
    }

    @Override
    public List<String> publishedEvents() {
        return List.copyOf(events);
    }
}
