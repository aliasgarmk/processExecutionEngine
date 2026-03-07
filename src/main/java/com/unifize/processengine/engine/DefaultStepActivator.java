package com.unifize.processengine.engine;

import com.unifize.processengine.model.AssigneeRuleType;
import com.unifize.processengine.model.EscalationPolicy;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;
import com.unifize.processengine.model.StepType;
import com.unifize.processengine.support.SequenceGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DefaultStepActivator implements StepActivator {
    private final EscalationScheduler escalationScheduler;
    private final ClockProvider clockProvider;
    private final SequenceGenerator sequenceGenerator;

    public DefaultStepActivator(EscalationScheduler escalationScheduler, ClockProvider clockProvider, SequenceGenerator sequenceGenerator) {
        this.escalationScheduler = escalationScheduler;
        this.clockProvider = clockProvider;
        this.sequenceGenerator = sequenceGenerator;
    }

    @Override
    public List<StepState> activateStep(ProcessInstance instance, StepDefinition stepDefinition) {
        List<String> assignees = resolveAssignees(stepDefinition, instance);
        List<StepState> created = new ArrayList<>();
        for (String assignee : assignees) {
            StepState stepState = new StepState(
                    UUID.randomUUID().toString(),
                    instance.instanceId(),
                    stepDefinition.stepId(),
                    assignee,
                    StepStatus.IN_PROGRESS,
                    clockProvider.now(),
                    null,
                    stepDefinition.stepType() == StepType.PARALLEL_APPROVAL ? assignee : null,
                    sequenceGenerator.next()
            );
            created.add(stepState);
            scheduleEscalationIfRequired(stepState, stepDefinition.escalationPolicy());
        }
        return created;
    }

    private List<String> resolveAssignees(StepDefinition stepDefinition, ProcessInstance instance) {
        return switch (stepDefinition.assigneeRule().type()) {
            case INITIATOR -> List.of(instance.initiator().userId());
            case USER_IDS -> stepDefinition.assigneeRule().userIds();
            case FIELD_VALUE_USER_ID -> List.of(String.valueOf(instance.getFieldValue(stepDefinition.assigneeRule().fieldName())));
        };
    }

    private void scheduleEscalationIfRequired(StepState stepState, EscalationPolicy escalationPolicy) {
        if (escalationPolicy != null) {
            escalationScheduler.scheduleEscalation(stepState, escalationPolicy);
        }
    }
}
