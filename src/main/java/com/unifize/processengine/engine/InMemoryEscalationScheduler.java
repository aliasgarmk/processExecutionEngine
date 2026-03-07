package com.unifize.processengine.engine;

import com.unifize.processengine.model.EscalationEvent;
import com.unifize.processengine.model.EscalationPolicy;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEscalationScheduler implements EscalationScheduler {
    private final Map<String, EscalationEvent> eventsByStepState = new ConcurrentHashMap<>();

    @Override
    public void scheduleEscalation(StepState stepState, EscalationPolicy policy) {
        eventsByStepState.put(stepState.stepStateId(), new EscalationEvent(
                stepState.instanceId(),
                stepState.stepStateId(),
                StepStatus.IN_PROGRESS,
                policy.threshold(),
                policy.reason()
        ));
    }

    @Override
    public void cancelEscalation(String stepStateId) {
        eventsByStepState.remove(stepStateId);
    }

    @Override
    public List<EscalationEvent> getScheduledEvents() {
        return new ArrayList<>(eventsByStepState.values());
    }
}
