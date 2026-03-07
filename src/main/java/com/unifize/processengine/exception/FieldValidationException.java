package com.unifize.processengine.exception;

import com.unifize.processengine.model.ValidationResult;

public final class FieldValidationException extends Exception {
    private final ValidationResult validationResult;

    public FieldValidationException(ValidationResult validationResult) {
        super("Field validation failed");
        this.validationResult = validationResult;
    }

    public ValidationResult validationResult() {
        return validationResult;
    }
}
