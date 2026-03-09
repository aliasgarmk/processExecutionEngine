package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.StepType;
import com.unifize.processengine.model.User;
import com.unifize.processengine.support.SequenceGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DefaultStepActivator implements StepActivator {
    private final EscalationScheduler escalationScheduler;
    private final ClockProvider clockProvider;
    private final SequenceGenerator sequenceGenerator;
    private final UserResolver userResolver;

    public DefaultStepActivator(EscalationScheduler escalationScheduler, ClockProvider clockProvider,
                                SequenceGenerator sequenceGenerator, UserResolver userResolver) {
        this.escalationScheduler = escalationScheduler;
        this.clockProvider = clockProvider;
        this.sequenceGenerator = sequenceGenerator;
        this.userResolver = userResolver;
    }

    @Override
    public List<StepState> activateStep(ProcessInstance instance, StepDefinition stepDefinition) {
        List<User> assignees = resolveAssignees(stepDefinition, instance);
        List<StepState> created = new ArrayList<>();
        for (User assignee : assignees) {
            StepState stepState = new StepState(
                    UUID.randomUUID().toString(),
                    instance.instanceId(),
                    stepDefinition.stepId(),
                    assignee.userId(),
                    StepStatus.PENDING,
                    clockProvider.now(),    // createdAt
                    null,                   // openedAt — set by openStep
                    null,                   // completedAt
                    stepDefinition.stepType() == StepType.PARALLEL_APPROVAL ? assignee.userId() : null,
                    sequenceGenerator.next()
            );
            created.add(stepState);
        }
        return created;
    }

    /**
     * Called by ProcessEngine.openStep after transitioning PENDING → IN_PROGRESS,
     * so escalation is only scheduled once the actor has actually started the step.
     */
    public void scheduleEscalationIfRequired(StepState stepState, StepDefinition stepDefinition) {
        if (stepDefinition.escalationPolicy() != null) {
            escalationScheduler.scheduleEscalation(stepState, stepDefinition.escalationPolicy());
        }
    }

    private List<User> resolveAssignees(StepDefinition stepDefinition, ProcessInstance instance) {
        return switch (stepDefinition.assigneeRule().type()) {
            case INITIATOR -> List.of(instance.initiator());
            case USER_IDS -> userResolver.resolveAll(stepDefinition.assigneeRule().userIds());
            case FIELD_VALUE_USER_ID -> List.of(userResolver.resolve(
                    String.valueOf(instance.getFieldValue(stepDefinition.assigneeRule().fieldName()))));
        };
    }
}
