package com.unifize.processengine.engine;

import com.unifize.processengine.exception.NoRoutingMatchException;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.RoutingRule;

import java.util.List;
import java.util.Map;

public final class DefaultRoutingRuleEvaluator implements RoutingRuleEvaluator {
    private final ExpressionEvaluator expressionEvaluator;

    public DefaultRoutingRuleEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public List<String> resolveNextSteps(ProcessDefinition definition, String completedStepId,
                                         ProcessInstance instance) {
        List<RoutingRule> rules = definition.routingRules().stream()
                .filter(rule -> rule.sourceStepId().equals(completedStepId))
                .toList();

        // A step with no routing rules at all is a terminal step — return empty to signal process end.
        if (rules.isEmpty()) {
            return List.of();
        }

        RoutingRule defaultRule = null;
        for (RoutingRule rule : rules) {
            if (rule.defaultRoute()) {
                defaultRule = rule;
                continue;
            }
            if (evaluateCondition(rule, instance.fieldValues())) {
                return rule.targetStepIds();
            }
        }
        if (defaultRule != null) {
            return defaultRule.targetStepIds();
        }

        // Rules exist but none matched and there is no default — this is a malformed definition.
        throw new NoRoutingMatchException(
                "No routing rule matched for step '" + completedStepId
                + "' and no default route is declared. Check the definition for missing conditions.");
    }

    @Override
    public boolean evaluateCondition(RoutingRule rule, Map<String, Object> fieldValues) {
        return expressionEvaluator.evaluate(rule.condition(), fieldValues);
    }
}
