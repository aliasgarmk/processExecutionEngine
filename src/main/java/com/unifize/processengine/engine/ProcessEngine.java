package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.Action;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The single public entry point into the engine.
 * Owns no business logic — its only job is to sequence calls to specialist collaborators
 * in the correct order and handle the transaction boundary.
 */
public final class ProcessEngine {
    private final DefinitionValidator definitionValidator;
    private final DefinitionRegistry definitionRegistry;
    private final InstanceFactory instanceFactory;
    private final DefaultStepActivator stepActivator;
    private final TransitionGuard transitionGuard;
    private final FieldValidator fieldValidator;
    private final RoutingRuleEvaluator routingRuleEvaluator;
    private final ParallelQuorumChecker quorumChecker;
    private final AuditWriter auditWriter;
    private final StateStore stateStore;
    private final EscalationScheduler escalationScheduler;
    private final EventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final UserResolver userResolver;

    public ProcessEngine(
            DefinitionValidator definitionValidator,
            DefinitionRegistry definitionRegistry,
            InstanceFactory instanceFactory,
            DefaultStepActivator stepActivator,
            TransitionGuard transitionGuard,
            FieldValidator fieldValidator,
            RoutingRuleEvaluator routingRuleEvaluator,
            ParallelQuorumChecker quorumChecker,
            AuditWriter auditWriter,
            StateStore stateStore,
            EscalationScheduler escalationScheduler,
            EventPublisher eventPublisher,
            ClockProvider clockProvider,
            UserResolver userResolver
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
        this.userResolver = userResolver;
    }

    /**
     * Validates and registers a process definition.
     * Throws DefinitionValidationException on any structural or routing violation.
     */
    public void loadDefinition(ProcessDefinition definition) throws DefinitionValidationException {
        definitionValidator.validate(definition);
        definitionValidator.compilePatterns(definition);
        definitionRegistry.register(definition);
    }

    /**
     * Starts a new process instance from the latest published version of the given definition.
     * The first step is created in PENDING status; call openStep to begin working on it.
     */
    public ProcessInstance startProcess(String definitionId, User initiator, Map<String, Object> fields)
            throws FieldValidationException {
        ProcessDefinition definition = definitionRegistry.resolveLatest(definitionId);
        StepDefinition firstStep = definition.steps().getFirst();

        Action submitAction = Action.submit(fields);
        ValidationResult validationResult = fieldValidator.validate(firstStep, submitAction);
        if (!validationResult.isValid()) {
            throw new FieldValidationException(validationResult);
        }

        ProcessInstance instance = instanceFactory.createInstance(definition, initiator, fields);
        List<StepState> initialStepStates = stepActivator.activateStep(instance, firstStep);
        instance = instance.withActiveStepIds(Set.of(firstStep.stepId()));

        for (StepState stepState : initialStepStates) {
            auditWriter.record(instance.instanceId(), stepState.stepStateId(), initiator,
                    null, StepStatus.PENDING, submitAction);
        }

        stateStore.saveInstance(instance, initialStepStates);
        eventPublisher.publishProcessStarted(instance);
        return stateStore.loadInstance(instance.instanceId());
    }

    /**
     * Transitions a PENDING step to IN_PROGRESS for the given actor.
     * Must be called before advanceStep can be used on the step.
     */
    public void openStep(String instanceId, String stepId, User actor)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            OptimisticLockException {
        ProcessInstance instance = stateStore.loadInstance(instanceId);
        transitionGuard.assertInstanceIsActive(instance);
        transitionGuard.assertStepIsActive(instance, stepId);

        ProcessDefinition definition = definitionRegistry.resolveVersion(
                instance.definitionId(), instance.definitionVersion());
        StepDefinition stepDefinition = findStepDefinition(definition, stepId);

        boolean isSystemActor = User.SYSTEM_USER_ID.equals(actor.userId());
        StepState pendingState = stateStore.loadStepStatesForStep(instanceId, stepId).stream()
                .filter(s -> s.status() == StepStatus.PENDING)
                .filter(s -> isSystemActor
                        || stepDefinition.stepType() != StepType.PARALLEL_APPROVAL
                        || Objects.equals(s.assignedTo(), actor.userId()))
                .findFirst()
                .orElseThrow(() -> new InvalidTransitionException(
                        "No pending step state found for actor and step"));

        transitionGuard.assertActorIsAuthorised(stepDefinition, instance, pendingState, actor);

        Action openAction = Action.open();
        StepState openedState = pendingState.withStatus(StepStatus.IN_PROGRESS, clockProvider.now());

        auditWriter.record(instanceId, openedState.stepStateId(), actor,
                StepStatus.PENDING, StepStatus.IN_PROGRESS, openAction);

        stepActivator.scheduleEscalationIfRequired(openedState, stepDefinition);

        stateStore.updateInstance(instance, List.of(openedState));
        eventPublisher.publishStepTransitioned(openedState, openAction, actor);
    }

    /**
     * Advances a step by applying the actor action.
     * The step must already be IN_PROGRESS (call openStep first if it is PENDING).
     */
    public StepResult advanceStep(String instanceId, String stepId, Action action, User actor)
            throws InactiveInstanceException, InvalidTransitionException, UnauthorisedTransitionException,
            FieldValidationException, OptimisticLockException {

        ProcessInstance instance = stateStore.loadInstance(instanceId);
        transitionGuard.assertInstanceIsActive(instance);
        transitionGuard.assertStepIsActive(instance, stepId);

        ProcessDefinition definition = definitionRegistry.resolveVersion(
                instance.definitionId(), instance.definitionVersion());
        StepDefinition stepDefinition = findStepDefinition(definition, stepId);
        StepState currentStepState = findCurrentStepState(instanceId, stepId,
                actor.userId(), stepDefinition.stepType());

        transitionGuard.assertStepIsInProgress(currentStepState);
        transitionGuard.assertActorIsAuthorised(stepDefinition, instance, currentStepState, actor);
        transitionGuard.assertActionIsValid(stepDefinition, currentStepState, action);

        ValidationResult validationResult = fieldValidator.validate(stepDefinition, action);
        if (!validationResult.isValid()) {
            throw new FieldValidationException(validationResult);
        }

        instance = instance.withFieldValues(action.getFields());

        StepStatus resultingStatus = action.resolvedStatus();
        StepState updatedCurrent = currentStepState.withStatus(resultingStatus, clockProvider.now());

        List<StepState> mutatedStepStates = new ArrayList<>();
        mutatedStepStates.add(updatedCurrent);
        escalationScheduler.cancelEscalation(updatedCurrent.stepStateId());

        AuditEntry stepAuditEntry = auditWriter.record(
                instance.instanceId(), updatedCurrent.stepStateId(),
                actor, currentStepState.status(), resultingStatus, action);

        List<String> nextStepIds;
        List<String> remainingParticipants = List.of();

        if (stepDefinition.stepType() == StepType.PARALLEL_APPROVAL) {
            List<StepState> participantStates = quorumChecker.mergeUpdatedState(
                    stateStore.loadStepStatesForStep(instanceId, stepId), updatedCurrent);

            if (quorumChecker.hasAnyRejection(participantStates)
                    || quorumChecker.isQuorumSatisfied(stepDefinition.quorumPolicy(), participantStates)) {
                nextStepIds = routingRuleEvaluator.resolveNextSteps(definition, stepId, instance);
            } else {
                remainingParticipants = quorumChecker.getRemainingParticipants(participantStates);
                stateStore.updateInstance(instance, mutatedStepStates);
                eventPublisher.publishStepTransitioned(updatedCurrent, action, actor);
                return new StepResult(instanceId, stepId, resultingStatus,
                        List.of(), remainingParticipants, false, stepAuditEntry);
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
                auditWriter.record(instance.instanceId(), stepState.stepStateId(),
                        actor, null, StepStatus.PENDING, Action.submit(Map.of()));
            }
            if (action.getType() == ActionType.ESCALATE && !activated.isEmpty()) {
                StepState escalatedState = activated.getFirst();
                eventPublisher.publishEscalationTriggered(
                        escalatedState,
                        userResolver.resolve(escalatedState.assignedTo()));
            }
        }

        instance = instance.withActiveStepIds(activeStepIds).completeIfNoActiveSteps();

        stateStore.updateInstance(instance, mutatedStepStates);
        eventPublisher.publishStepTransitioned(updatedCurrent, action, actor);
        if (!instance.isActive()) {
            eventPublisher.publishProcessCompleted(instance);
        }

        return new StepResult(instanceId, stepId, resultingStatus, nextStepIds,
                remainingParticipants, !instance.isActive(), stepAuditEntry);
    }

    /**
     * Returns the complete audit trail for an instance, ordered by sequence number ascending.
     */
    public List<AuditEntry> getAuditTrail(String instanceId) {
        return auditWriter.getTrailForInstance(instanceId);
    }

    private StepDefinition findStepDefinition(ProcessDefinition definition, String stepId) {
        return definition.steps().stream()
                .filter(step -> Objects.equals(step.stepId(), stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown step: " + stepId));
    }

    private StepState findCurrentStepState(String instanceId, String stepId,
                                            String actorUserId, StepType stepType)
            throws InvalidTransitionException {
        List<StepState> stepStates = stateStore.loadStepStatesForStep(instanceId, stepId);
        boolean isSystemActor = User.SYSTEM_USER_ID.equals(actorUserId);
        return stepStates.stream()
                .filter(s -> isSystemActor
                        || stepType != StepType.PARALLEL_APPROVAL
                        || Objects.equals(s.assignedTo(), actorUserId))
                .findFirst()
                .orElseThrow(() -> new InvalidTransitionException(
                        "No step state found for actor and step"));
    }
}
