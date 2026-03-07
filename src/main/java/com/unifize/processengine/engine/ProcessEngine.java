package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.ActionType;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepResult;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.StepType;
import com.unifize.processengine.model.User;
import com.unifize.processengine.model.ValidationResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProcessEngine {
    private final DefinitionValidator definitionValidator;
    private final DefinitionRegistry definitionRegistry;
    private final InstanceFactory instanceFactory;
    private final StepActivator stepActivator;
    private final TransitionGuard transitionGuard;
    private final FieldValidator fieldValidator;
    private final RoutingRuleEvaluator routingRuleEvaluator;
    private final ParallelQuorumChecker quorumChecker;
    private final AuditWriter auditWriter;
    private final StateStore stateStore;
    private final EscalationScheduler escalationScheduler;
    private final EventPublisher eventPublisher;
    private final ClockProvider clockProvider;

    public ProcessEngine(
            DefinitionValidator definitionValidator,
            DefinitionRegistry definitionRegistry,
            InstanceFactory instanceFactory,
            StepActivator stepActivator,
            TransitionGuard transitionGuard,
            FieldValidator fieldValidator,
            RoutingRuleEvaluator routingRuleEvaluator,
            ParallelQuorumChecker quorumChecker,
            AuditWriter auditWriter,
            StateStore stateStore,
            EscalationScheduler escalationScheduler,
            EventPublisher eventPublisher,
            ClockProvider clockProvider
    ) {
        this.definitionValidator = definitionValidator;
        this.definitionRegistry = definitionRegistry;
        this.instanceFactory = instanceFactory;
        this.stepActivator = stepActivator;
        this.transitionGuard = transitionGuard;
        this.fieldValidator = fieldValidator;
        this.routingRuleEvaluator = routingRuleEvaluator;
        this.quorumChecker = quorumChecker;
        this.auditWriter = auditWriter;
        this.stateStore = stateStore;
        this.escalationScheduler = escalationScheduler;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
    }

    public ProcessDefinition loadDefinition(ProcessDefinition definition) throws DefinitionValidationException {
        definitionValidator.validate(definition);
        return definitionRegistry.register(definition);
    }

    public ProcessInstance startProcess(String definitionId, User initiator, Map<String, Object> fields)
            throws FieldValidationException {
        ProcessDefinition definition = definitionRegistry.resolveLatest(definitionId);
        StepDefinition firstStep = definition.steps().getFirst();
        ValidationResult validationResult = fieldValidator.validate(firstStep, fields);
        if (!validationResult.isValid()) {
            throw new FieldValidationException(validationResult);
        }

        ProcessInstance instance = instanceFactory.createInstance(definition, initiator, fields);
        List<StepState> initialStepStates = stepActivator.activateStep(instance, firstStep);
        instance.setActiveStepIds(Set.of(firstStep.stepId()));

        Instant occurredAt = clockProvider.now();
        List<AuditEntry> audits = initialStepStates.stream()
                .map(stepState -> auditWriter.record(
                        instance.instanceId(),
                        stepState.stepStateId(),
                        initiator,
                        null,
                        StepStatus.IN_PROGRESS,
                        ActionType.SUBMIT,
                        "Process started",
                        occurredAt
                ))
                .toList();

        stateStore.saveInstance(instance, initialStepStates, audits);
        eventPublisher.publishProcessStarted(instance);
        return stateStore.loadInstance(instance.instanceId());
    }

    public StepResult advanceStep(String instanceId, String stepId, ActionType action, User actor, Map<String, Object> fields)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {
        ProcessInstance instance = stateStore.loadInstance(instanceId);
        transitionGuard.assertInstanceIsActive(instance);
        transitionGuard.assertStepIsActive(instance, stepId);

        ProcessDefinition definition = definitionRegistry.resolveVersion(instance.definitionId(), instance.definitionVersion());
        StepDefinition stepDefinition = findStepDefinition(definition, stepId);
        StepState currentStepState = findCurrentStepState(instanceId, stepId, actor.userId(), stepDefinition.stepType());

        transitionGuard.assertActorIsAuthorised(stepDefinition, instance, currentStepState, actor);
        transitionGuard.assertActionAllowed(stepDefinition, currentStepState, action);

        Map<String, Object> mergedFields = new HashMap<>(instance.fieldValues());
        mergedFields.putAll(fields);
        ValidationResult validationResult = fieldValidator.validate(stepDefinition, mergedFields);
        if (!validationResult.isValid()) {
            throw new FieldValidationException(validationResult);
        }

        instance.putFieldValues(fields);
        Instant now = clockProvider.now();
        StepStatus resultingStatus = toStepStatus(action);
        StepState updatedCurrent = currentStepState.withStatus(resultingStatus, now);
        List<StepState> mutatedStepStates = new ArrayList<>();
        mutatedStepStates.add(updatedCurrent);
        List<AuditEntry> auditEntries = new ArrayList<>();
        auditEntries.add(auditWriter.record(
                instance.instanceId(),
                updatedCurrent.stepStateId(),
                actor,
                currentStepState.status(),
                resultingStatus,
                action,
                null,
                now
        ));
        escalationScheduler.cancelEscalation(updatedCurrent.stepStateId());

        List<String> nextStepIds = List.of();
        List<String> remainingParticipants = List.of();

        if (stepDefinition.stepType() == StepType.PARALLEL_APPROVAL) {
            List<StepState> participantStates = mergeParticipantStates(
                    stateStore.loadStepStatesForStep(instanceId, stepId),
                    updatedCurrent
            );
            if (quorumChecker.hasAnyRejection(participantStates)) {
                nextStepIds = routingRuleEvaluator.resolveNextSteps(definition, stepId, instance);
            } else if (quorumChecker.isQuorumSatisfied(stepDefinition.quorumPolicy(), participantStates)) {
                nextStepIds = routingRuleEvaluator.resolveNextSteps(definition, stepId, instance);
            } else {
                remainingParticipants = quorumChecker.getRemainingParticipants(participantStates);
                stateStore.updateInstance(instance, mutatedStepStates, auditEntries);
                eventPublisher.publishStepTransitioned(updatedCurrent, action, actor);
                return new StepResult(instanceId, stepId, resultingStatus, List.of(), remainingParticipants, false);
            }
        } else {
            nextStepIds = routingRuleEvaluator.resolveNextSteps(definition, stepId, instance);
        }

        Set<String> activeStepIds = new HashSet<>(instance.activeStepIds());
        activeStepIds.remove(stepId);

        for (String nextStepId : nextStepIds) {
            StepDefinition nextStep = findStepDefinition(definition, nextStepId);
            List<StepState> activated = stepActivator.activateStep(instance, nextStep);
            mutatedStepStates.addAll(activated);
            activeStepIds.add(nextStepId);
            for (StepState stepState : activated) {
                auditEntries.add(auditWriter.record(
                        instance.instanceId(),
                        stepState.stepStateId(),
                        actor,
                        null,
                        StepStatus.IN_PROGRESS,
                        ActionType.SUBMIT,
                        "Step activated",
                        now
                ));
            }
            if (action == ActionType.ESCALATE && !activated.isEmpty()) {
                eventPublisher.publishEscalationTriggered(activated.getFirst(), new User(activated.getFirst().assignedTo(), activated.getFirst().assignedTo()));
            }
        }

        instance.setActiveStepIds(activeStepIds);
        instance.completeIfNoActiveSteps();

        stateStore.updateInstance(instance, mutatedStepStates, auditEntries);
        eventPublisher.publishStepTransitioned(updatedCurrent, action, actor);
        if (!instance.isActive()) {
            eventPublisher.publishProcessCompleted(instance);
        }

        return new StepResult(instanceId, stepId, resultingStatus, nextStepIds, remainingParticipants, !instance.isActive());
    }

    public List<AuditEntry> getAuditTrail(String instanceId) {
        return auditWriter.getTrailForInstance(instanceId);
    }

    private StepDefinition findStepDefinition(ProcessDefinition definition, String stepId) {
        return definition.steps().stream()
                .filter(step -> Objects.equals(step.stepId(), stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown step: " + stepId));
    }

    private StepState findCurrentStepState(String instanceId, String stepId, String actorUserId, StepType stepType)
            throws InvalidTransitionException {
        List<StepState> stepStates = stateStore.loadStepStatesForStep(instanceId, stepId);
        boolean systemActor = "SYSTEM".equals(actorUserId);
        return stepStates.stream()
                .filter(stepState -> stepState.status() == StepStatus.IN_PROGRESS)
                .filter(stepState -> systemActor
                        || stepType != StepType.PARALLEL_APPROVAL
                        || Objects.equals(stepState.assignedTo(), actorUserId))
                .findFirst()
                .orElseThrow(() -> new InvalidTransitionException("No active step state found for actor and step"));
    }

    private List<StepState> mergeParticipantStates(List<StepState> existing, StepState updated) {
        List<StepState> merged = new ArrayList<>();
        boolean replaced = false;
        for (StepState stepState : existing) {
            if (stepState.stepStateId().equals(updated.stepStateId())) {
                merged.add(updated);
                replaced = true;
            } else {
                merged.add(stepState);
            }
        }
        if (!replaced) {
            merged.add(updated);
        }
        return merged;
    }

    private StepStatus toStepStatus(ActionType actionType) {
        return switch (actionType) {
            case APPROVE, SUBMIT, REOPEN -> StepStatus.COMPLETED;
            case REJECT -> StepStatus.REJECTED;
            case ESCALATE -> StepStatus.ESCALATED;
            case REASSIGN -> StepStatus.IN_PROGRESS;
        };
    }
}
