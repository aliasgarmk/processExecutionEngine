package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.RoutingRule;

import java.util.List;
import java.util.Map;

public interface RoutingRuleEvaluator {
    List<String> resolveNextSteps(ProcessDefinition definition, String completedStepId, ProcessInstance instance);

    boolean evaluateCondition(RoutingRule rule, Map<String, Object> fieldValues);
}
