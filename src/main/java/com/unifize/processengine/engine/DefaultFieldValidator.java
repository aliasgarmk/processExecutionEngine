package com.unifize.processengine.engine;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.FieldSchema;
import com.unifize.processengine.model.FieldViolation;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.ValidationResult;
import com.unifize.processengine.model.ValidationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultFieldValidator implements FieldValidator {
    private final ExpressionEvaluator expressionEvaluator;

    public DefaultFieldValidator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public ValidationResult validate(StepDefinition stepDefinition, Action action) {
        List<FieldViolation> violations = new ArrayList<>();
        Map<String, Object> fields = action.getFields();
        violations.addAll(checkRequiredFields(stepDefinition, fields));
        violations.addAll(checkRegexPatterns(stepDefinition, fields));
        violations.addAll(checkCrossFieldRules(stepDefinition, fields));
        return new ValidationResult(violations);
    }

    private List<FieldViolation> checkRequiredFields(StepDefinition stepDefinition, Map<String, Object> fields) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldSchema schema : stepDefinition.fieldSchemas()) {
            Object value = fields.get(schema.name());
            if (schema.required() && (value == null || (value instanceof String s && s.isBlank()))) {
                violations.add(new FieldViolation(schema.name(), "Field is required"));
            }
        }
        return violations;
    }

    private List<FieldViolation> checkRegexPatterns(StepDefinition stepDefinition, Map<String, Object> fields) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldSchema schema : stepDefinition.fieldSchemas()) {
            if (schema.compiledPattern() == null) {
                continue;
            }
            Object value = fields.get(schema.name());
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                if (!schema.compiledPattern().matcher(stringValue).matches()) {
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
