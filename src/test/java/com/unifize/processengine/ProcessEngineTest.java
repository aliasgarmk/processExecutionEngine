package com.unifize.processengine;

import com.unifize.processengine.engine.ProcessEngine;
import com.unifize.processengine.engine.ProcessEngineFactory;
import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.AssigneeRule;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.EscalationPolicy;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.QuorumMode;
import com.unifize.processengine.model.QuorumPolicy;
import com.unifize.processengine.model.RoutingRule;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepResult;
import com.unifize.processengine.model.StepType;
import com.unifize.processengine.model.User;
import com.unifize.processengine.model.ValidationRule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessEngineTest {
    private static final User INITIATOR = new User("initiator", "Initiator");
    private static final User INVESTIGATOR = new User("investigator-1", "Investigator");
    private static final User REVIEWER_1 = new User("reviewer-1", "Reviewer 1");
    private static final User REVIEWER_2 = new User("reviewer-2", "Reviewer 2");
    private static final User REVIEWER_3 = new User("reviewer-3", "Reviewer 3");
    private static final User IMPLEMENTER = new User("implementer-1", "Implementer");
    private static final User MANAGER = new User("manager-1", "Manager");

    @Test
    void loadsVersionedDefinitionsAndPinsInstancesToStartingVersion()
            throws DefinitionValidationException, FieldValidationException {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();

        engine.loadDefinition(sequentialDefinition("definition-a"));
        int v1Version = runtime.definitionRegistry().resolveLatest("definition-a").version();
        ProcessInstance instance = engine.startProcess("definition-a", INITIATOR, Map.of("severity", "Minor"));

        engine.loadDefinition(parallelDefinition("definition-a", QuorumMode.MAJORITY));
        int v2Version = runtime.definitionRegistry().resolveLatest("definition-a").version();

        assertEquals(1, v1Version);
        assertEquals(2, v2Version);
        assertEquals(1, instance.definitionVersion());
    }

    @Test
    void executesSequentialAndParallelFlowEndToEnd() throws Exception {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();
        engine.loadDefinition(parallelDefinition("capa", QuorumMode.MAJORITY));

        ProcessInstance instance = engine.startProcess("capa", INITIATOR, Map.of(
                "severity", "Critical",
                "investigator", INVESTIGATOR.userId()
        ));

        engine.openStep(instance.instanceId(), "initiation", INITIATOR);
        StepResult initiationResult = engine.advanceStep(
                instance.instanceId(), "initiation", Action.submit(Map.of()), INITIATOR);
        assertEquals(List.of("investigation"), initiationResult.nextStepIds());

        engine.openStep(instance.instanceId(), "investigation", INVESTIGATOR);
        StepResult investigationResult = engine.advanceStep(
                instance.instanceId(), "investigation",
                Action.submit(Map.of("rootCause", "Supplier issue")), INVESTIGATOR);
        assertEquals(List.of("review"), investigationResult.nextStepIds());

        engine.openStep(instance.instanceId(), "review", REVIEWER_1);
        StepResult firstApproval = engine.advanceStep(
                instance.instanceId(), "review", Action.approve(), REVIEWER_1);
        assertTrue(firstApproval.nextStepIds().isEmpty());
        assertEquals(2, firstApproval.remainingParticipants().size());

        engine.openStep(instance.instanceId(), "review", REVIEWER_2);
        StepResult secondApproval = engine.advanceStep(
                instance.instanceId(), "review", Action.approve(), REVIEWER_2);
        assertEquals(List.of("implementation"), secondApproval.nextStepIds());

        engine.openStep(instance.instanceId(), "implementation", IMPLEMENTER);
        StepResult completion = engine.advanceStep(
                instance.instanceId(), "implementation",
                Action.submit(Map.of("actionTaken", "Supplier replaced")), IMPLEMENTER);
        assertTrue(completion.processCompleted());

        List<AuditEntry> auditEntries = engine.getAuditTrail(instance.instanceId());
        assertTrue(auditEntries.size() >= 6);
        assertAuditOrdered(auditEntries);
    }

    @Test
    void rejectsInvalidDefinitionWithCycle() {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();

        ProcessDefinition invalid = new ProcessDefinition(
                "invalid",
                0,
                "Invalid",
                List.of(
                        new StepDefinition("a", StepType.TASK, AssigneeRule.initiator(), List.of(), List.of(), null, null, null),
                        new StepDefinition("b", StepType.TASK, AssigneeRule.initiator(), List.of(), List.of(), null, null, null)
                ),
                List.of(
                        new RoutingRule("a", null, true, List.of("b")),
                        new RoutingRule("b", null, true, List.of("a"))
                ),
                Instant.now(),
                "admin"
        );

        assertThrows(DefinitionValidationException.class, () -> engine.loadDefinition(invalid));
    }

    @Test
    void enforcesFieldValidationRules() throws Exception {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();
        engine.loadDefinition(parallelDefinition("validation", QuorumMode.ALL));

        ProcessInstance instance = engine.startProcess("validation", INITIATOR, Map.of(
                "severity", "Critical",
                "investigator", INVESTIGATOR.userId()
        ));

        engine.openStep(instance.instanceId(), "initiation", INITIATOR);
        engine.advanceStep(instance.instanceId(), "initiation", Action.submit(Map.of()), INITIATOR);

        engine.openStep(instance.instanceId(), "investigation", INVESTIGATOR);
        Executable invalidAdvance = () -> engine.advanceStep(
                instance.instanceId(), "investigation", Action.submit(Map.of()), INVESTIGATOR);

        FieldValidationException error = assertThrows(FieldValidationException.class, invalidAdvance);
        assertFalse(error.validationResult().isValid());
    }

    @Test
    void processesEscalationsOnlyWhenStillApplicable() throws Exception {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();
        engine.loadDefinition(escalationDefinition("escalation"));

        ProcessInstance instance = engine.startProcess("escalation", INITIATOR, Map.of());
        // Escalation is scheduled after openStep (step must be IN_PROGRESS first)
        engine.openStep(instance.instanceId(), "triage", INITIATOR);
        assertEquals(1, runtime.escalationScheduler().getScheduledEvents().size());

        runtime.escalationWorker().handleEscalationEvent(runtime.escalationScheduler().getScheduledEvents().getFirst());

        List<AuditEntry> auditEntries = engine.getAuditTrail(instance.instanceId());
        assertTrue(auditEntries.stream().anyMatch(entry -> entry.actionType() == ActionType.ESCALATE));
        assertTrue(runtime.eventPublisher().publishedEvents().stream()
                .anyMatch(event -> event.startsWith("ESCALATION_TRIGGERED:")));
    }

    @Test
    void stateStoreRejectsStaleVersionUpdates() throws Exception {
        ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
        ProcessEngine engine = runtime.processEngine();
        engine.loadDefinition(sequentialDefinition("locks"));

        ProcessInstance instance = engine.startProcess("locks", INITIATOR, Map.of("severity", "Minor"));
        ProcessInstance firstLoad = runtime.stateStore().loadInstance(instance.instanceId());
        ProcessInstance staleLoad = runtime.stateStore().loadInstance(instance.instanceId());

        runtime.stateStore().updateInstance(firstLoad, List.of());

        assertThrows(OptimisticLockException.class,
                () -> runtime.stateStore().updateInstance(staleLoad, List.of()));
    }

    private static void assertAuditOrdered(List<AuditEntry> auditEntries) {
        long previous = 0;
        for (AuditEntry entry : auditEntries) {
            assertTrue(entry.sequenceNumber() > previous);
            previous = entry.sequenceNumber();
        }
    }

    private static ProcessDefinition sequentialDefinition(String definitionId) {
        return new ProcessDefinition(
                definitionId,
                0,
                "Sequential",
                List.of(
                        new StepDefinition("initiation", StepType.TASK, AssigneeRule.initiator(), List.of(), List.of(), null, null, null),
                        new StepDefinition("implementation", StepType.TASK, AssigneeRule.users(List.of(IMPLEMENTER.userId())), List.of(), List.of(), null, null, null)
                ),
                List.of(new RoutingRule("initiation", null, true, List.of("implementation"))),
                Instant.now(),
                "admin"
        );
    }

    private static ProcessDefinition parallelDefinition(String definitionId, QuorumMode quorumMode) {
        return new ProcessDefinition(
                definitionId,
                0,
                "CAPA",
                List.of(
                        new StepDefinition("initiation", StepType.TASK, AssigneeRule.initiator(), List.of(), List.of(), null, null, null),
                        new StepDefinition(
                                "investigation",
                                StepType.TASK,
                                AssigneeRule.fromField("investigator"),
                                List.of(),
                                List.of(new ValidationRule("Root cause is required", "rootCause IS NOT EMPTY")),
                                null,
                                null,
                                null
                        ),
                        new StepDefinition(
                                "review",
                                StepType.PARALLEL_APPROVAL,
                                AssigneeRule.users(List.of(REVIEWER_1.userId(), REVIEWER_2.userId(), REVIEWER_3.userId())),
                                List.of(),
                                List.of(),
                                new QuorumPolicy(quorumMode),
                                null,
                                null
                        ),
                        new StepDefinition(
                                "implementation",
                                StepType.CHECKLIST,
                                AssigneeRule.users(List.of(IMPLEMENTER.userId())),
                                List.of(),
                                List.of(new ValidationRule("Action taken is required", "actionTaken IS NOT EMPTY")),
                                null,
                                null,
                                null
                        )
                ),
                List.of(
                        new RoutingRule("initiation", null, true, List.of("investigation")),
                        new RoutingRule("investigation", "severity == Critical", false, List.of("review")),
                        new RoutingRule("investigation", null, true, List.of("implementation")),
                        new RoutingRule("review", null, true, List.of("implementation"))
                ),
                Instant.now(),
                "admin"
        );
    }

    private static ProcessDefinition escalationDefinition(String definitionId) {
        return new ProcessDefinition(
                definitionId,
                0,
                "Escalation",
                List.of(
                        new StepDefinition(
                                "triage",
                                StepType.TASK,
                                AssigneeRule.initiator(),
                                List.of(),
                                List.of(),
                                null,
                                new EscalationPolicy(Duration.ofHours(4), MANAGER.userId(), "Overdue"),
                                null
                        ),
                        new StepDefinition(
                                "manager_review",
                                StepType.APPROVAL,
                                AssigneeRule.users(List.of(MANAGER.userId())),
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null
                        )
                ),
                List.of(new RoutingRule("triage", null, true, List.of("manager_review"))),
                Instant.now(),
                "admin"
        );
    }
}
