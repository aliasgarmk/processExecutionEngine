package com.unifize.processengine.model;

import java.time.Duration;

public record EscalationEvent(
        String instanceId,
        String stepStateId,
        StepStatus expectedStatus,
        Duration delay,
        String reason
) {
}
