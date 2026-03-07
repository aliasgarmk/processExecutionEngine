package com.unifize.processengine.support;

import java.util.concurrent.atomic.AtomicLong;

public final class AtomicSequenceGenerator implements SequenceGenerator {
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public long next() {
        return sequence.incrementAndGet();
    }
}
