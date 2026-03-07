package com.unifize.processengine.model;

import java.util.List;

public record ValidationResult(List<FieldViolation> violations) {
    public ValidationResult {
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
