package com.unifize.processengine.exception;

import java.util.List;

public final class DefinitionValidationException extends Exception {
    private final List<String> violations;

    public DefinitionValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
