package com.unifize.processengine.engine;

import com.unifize.processengine.model.FieldSchema;
import com.unifize.processengine.model.FieldViolation;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.ValidationResult;
import com.unifize.processengine.model.ValidationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DefaultFieldValidator implements FieldValidator {
    private final ExpressionEvaluator expressionEvaluator;

    public DefaultFieldValidator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public ValidationResult validate(StepDefinition stepDefinition, Map<String, Object> submittedFields) {
        List<FieldViolation> violations = new ArrayList<>();
        violations.addAll(checkRequiredFields(stepDefinition, submittedFields));
        violations.addAll(checkRegexPatterns(stepDefinition, submittedFields));
        violations.addAll(checkCrossFieldRules(stepDefinition, submittedFields));
        return new ValidationResult(violations);
    }

    private List<FieldViolation> checkRequiredFields(StepDefinition stepDefinition, Map<String, Object> fields) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldSchema schema : stepDefinition.fieldSchemas()) {
            Object value = fields.get(schema.name());
            if (schema.required() && (value == null || (value instanceof String stringValue && stringValue.isBlank()))) {
                violations.add(new FieldViolation(schema.name(), "Field is required"));
            }
        }
        return violations;
    }

    private List<FieldViolation> checkRegexPatterns(StepDefinition stepDefinition, Map<String, Object> fields) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldSchema schema : stepDefinition.fieldSchemas()) {
            if (schema.regex() == null || schema.regex().isBlank()) {
                continue;
            }
            Object value = fields.get(schema.name());
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                if (!Pattern.compile(schema.regex()).matcher(stringValue).matches()) {
                    violations.add(new FieldViolation(schema.name(), "Field does not match expected format"));
                }
            }
        }
        return violations;
    }

    private List<FieldViolation> checkCrossFieldRules(StepDefinition stepDefinition, Map<String, Object> fields) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ValidationRule rule : stepDefinition.validationRules()) {
            if (!expressionEvaluator.evaluate(rule.expression(), fields)) {
                violations.add(new FieldViolation("*", rule.message()));
            }
        }
        return violations;
    }
}
