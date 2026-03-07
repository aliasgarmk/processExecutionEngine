package com.unifize.processengine.engine;

import java.time.Instant;

public final class SystemClockProvider implements ClockProvider {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
