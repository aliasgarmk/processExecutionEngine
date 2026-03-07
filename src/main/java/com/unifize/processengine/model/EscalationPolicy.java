package com.unifize.processengine.model;

import java.time.Duration;

public record EscalationPolicy(
        Duration threshold,
        String escalationTargetUserId,
        String reason
) {
}
