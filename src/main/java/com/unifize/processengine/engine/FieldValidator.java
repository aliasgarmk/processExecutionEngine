package com.unifize.processengine.engine;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.ValidationResult;

public interface FieldValidator {
    /**
     * Validates submitted field values and action-specific requirements against the step definition.
     * Returns a ValidationResult containing all violations found; an empty result means valid.
     */
    ValidationResult validate(StepDefinition stepDefinition, Action action);
}
