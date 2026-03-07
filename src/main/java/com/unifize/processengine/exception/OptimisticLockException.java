package com.unifize.processengine.exception;

public final class OptimisticLockException extends Exception {
    public OptimisticLockException(String message) {
        super(message);
    }
}
