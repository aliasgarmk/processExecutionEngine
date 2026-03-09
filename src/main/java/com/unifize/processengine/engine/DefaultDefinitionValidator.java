package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.model.FieldSchema;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.RoutingRule;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class DefaultDefinitionValidator implements DefinitionValidator {
    @Override
    public void validate(ProcessDefinition definition) throws DefinitionValidationException {
        List<String> violations = new ArrayList<>();
        assertDefinitionBasics(definition, violations);
        assertNoDuplicateStepIds(definition, violations);
        assertAllRoutingTargetsExist(definition, violations);
        assertEveryStepHasExitPath(definition, violations);
        assertNoCyclicDependency(definition, violations);
        assertParallelStepsHaveQuorumPolicy(definition, violations);
        assertEverySourceHasSingleDefault(definition, violations);

        if (!violations.isEmpty()) {
            throw new DefinitionValidationException(violations);
        }
    }

    @Override
    public void compilePatterns(ProcessDefinition definition) throws DefinitionValidationException {
        List<String> violations = new ArrayList<>();
        for (StepDefinition step : definition.steps()) {
            for (FieldSchema schema : step.fieldSchemas()) {
                if (schema.regex() != null && !schema.regex().isBlank()) {
                    try {
                        schema.compiledPattern(); // triggers compilation; throws if invalid
                    } catch (PatternSyntaxException e) {
                        violations.add("Invalid regex on field '" + schema.name()
                                + "' in step '" + step.stepId() + "': " + e.getDescription());
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new DefinitionValidationException(violations);
        }
    }

    private void assertDefinitionBasics(ProcessDefinition definition, List<String> violations) {
        if (definition.definitionId() == null || definition.definitionId().isBlank()) {
            violations.add("definitionId is required");
        }
        if (definition.name() == null || definition.name().isBlank()) {
            violations.add("name is required");
        }
        if (definition.steps().isEmpty()) {
            violations.add("At least one step is required");
        }
    }

    private void assertNoDuplicateStepIds(ProcessDefinition definition, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (StepDefinition step : definition.steps()) {
            if (!seen.add(step.stepId())) {
                violations.add("Duplicate stepId: " + step.stepId());
            }
        }
    }

    private void assertAllRoutingTargetsExist(ProcessDefinition definition, List<String> violations) {
        Set<String> stepIds = definition.steps().stream().map(StepDefinition::stepId).collect(Collectors.toSet());
        for (RoutingRule rule : definition.routingRules()) {
            if (!stepIds.contains(rule.sourceStepId())) {
                violations.add("Routing source does not exist: " + rule.sourceStepId());
            }
            for (String target : rule.targetStepIds()) {
                if (!stepIds.contains(target)) {
                    violations.add("Routing target does not exist: " + target);
                }
            }
        }
    }

    private void assertEveryStepHasExitPath(ProcessDefinition definition, List<String> violations) {
        Map<String, Long> routeCount = definition.routingRules().stream()
                .collect(Collectors.groupingBy(RoutingRule::sourceStepId, Collectors.counting()));
        String lastStepId = definition.steps().get(definition.steps().size() - 1).stepId();
        for (StepDefinition step : definition.steps()) {
            if (Objects.equals(step.stepId(), lastStepId)) {
                continue;
            }
            if (routeCount.getOrDefault(step.stepId(), 0L) == 0L) {
                violations.add("Step has no exit path: " + step.stepId());
            }
        }
    }

    private void assertNoCyclicDependency(ProcessDefinition definition, List<String> violations) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (RoutingRule rule : definition.routingRules()) {
            adjacency.computeIfAbsent(rule.sourceStepId(), ignored -> new ArrayList<>()).addAll(rule.targetStepIds());
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (StepDefinition step : definition.steps()) {
            if (hasCycle(step.stepId(), adjacency, visited, inStack)) {
                violations.add("Cycle detected involving step: " + step.stepId());
                return;
            }
        }
    }

    private boolean hasCycle(String node, Map<String, List<String>> adjacency, Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        inStack.add(node);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            if (hasCycle(next, adjacency, visited, inStack)) {
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }

    private void assertParallelStepsHaveQuorumPolicy(ProcessDefinition definition, List<String> violations) {
        for (StepDefinition step : definition.steps()) {
            if (step.stepType() == StepType.PARALLEL_APPROVAL && step.quorumPolicy() == null) {
                violations.add("Parallel approval step missing quorum policy: " + step.stepId());
            }
        }
    }

    private void assertEverySourceHasSingleDefault(ProcessDefinition definition, List<String> violations) {
        Map<String, Integer> defaultCounts = new HashMap<>();
        for (RoutingRule rule : definition.routingRules()) {
            if (rule.defaultRoute()) {
                defaultCounts.merge(rule.sourceStepId(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : defaultCounts.entrySet()) {
            if (entry.getValue() > 1) {
                violations.add("Multiple default routes declared for step: " + entry.getKey());
            }
        }
    }
}
