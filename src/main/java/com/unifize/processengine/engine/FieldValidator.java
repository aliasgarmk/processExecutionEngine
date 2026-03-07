package com.unifize.processengine.engine;

import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.ValidationResult;

import java.util.Map;

public interface FieldValidator {
    ValidationResult validate(StepDefinition stepDefinition, Map<String, Object> submittedFields);
}
