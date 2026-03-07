package com.unifize.processengine.engine;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}
