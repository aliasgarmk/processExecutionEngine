package com.unifize.processengine.engine;

import com.unifize.processengine.model.EscalationEvent;
import com.unifize.processengine.model.EscalationPolicy;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface EscalationScheduler {
    void scheduleEscalation(StepState stepState, EscalationPolicy policy);

    void cancelEscalation(String stepStateId);

    List<EscalationEvent> getScheduledEvents();
}
