package com.unifize.processengine.api.dto.request;

import com.unifize.processengine.model.AssigneeRule;
import com.unifize.processengine.model.AssigneeRuleType;
import com.unifize.processengine.model.EscalationPolicy;
import com.unifize.processengine.model.FieldSchema;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.QuorumMode;
import com.unifize.processengine.model.QuorumPolicy;
import com.unifize.processengine.model.RoutingRule;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepType;
import com.unifize.processengine.model.ValidationRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record LoadDefinitionRequest(
        @NotBlank String definitionId,
        @NotBlank String name,
        String publishedBy,
        @NotEmpty @Valid List<StepDto> steps,
        @NotNull @Valid List<RoutingRuleDto> routingRules
) {

    public ProcessDefinition toDomain() {
        return new ProcessDefinition(
                definitionId,
                0,
                name,
                steps.stream().map(StepDto::toDomain).toList(),
                routingRules.stream().map(RoutingRuleDto::toDomain).toList(),
                Instant.now(),
                publishedBy != null ? publishedBy : "api"
        );
    }

    public record StepDto(
            @NotBlank String stepId,
            @NotNull StepType stepType,
            @NotNull @Valid AssigneeRuleDto assigneeRule,
            List<FieldSchemaDto> fieldSchemas,
            List<ValidationRuleDto> validationRules,
            QuorumPolicyDto quorumPolicy,
            EscalationPolicyDto escalationPolicy
    ) {
        StepDefinition toDomain() {
            return new StepDefinition(
                    stepId,
                    stepType,
                    assigneeRule.toDomain(),
                    fieldSchemas == null ? List.of()
                            : fieldSchemas.stream().map(FieldSchemaDto::toDomain).toList(),
                    validationRules == null ? List.of()
                            : validationRules.stream().map(ValidationRuleDto::toDomain).toList(),
                    quorumPolicy == null ? null : quorumPolicy.toDomain(),
                    escalationPolicy == null ? null : escalationPolicy.toDomain(),
                    null
            );
        }
    }

    public record AssigneeRuleDto(
            @NotNull AssigneeRuleType type,
            List<String> userIds,
            String fieldName
    ) {
        AssigneeRule toDomain() {
            return switch (type) {
                case INITIATOR -> AssigneeRule.initiator();
                case USER_IDS -> AssigneeRule.users(userIds != null ? userIds : List.of());
                case FIELD_VALUE_USER_ID -> AssigneeRule.fromField(fieldName);
            };
        }
    }

    public record FieldSchemaDto(
            @NotBlank String name,
            boolean required,
            String regex
    ) {
        FieldSchema toDomain() {
            return new FieldSchema(name, required, regex);
        }
    }

    public record ValidationRuleDto(
            @NotBlank String message,
            @NotBlank String expression
    ) {
        ValidationRule toDomain() {
            return new ValidationRule(message, expression);
        }
    }

    public record QuorumPolicyDto(@NotNull QuorumMode mode) {
        QuorumPolicy toDomain() {
            return new QuorumPolicy(mode);
        }
    }

    public record EscalationPolicyDto(
            @NotBlank String thresholdIso,
            @NotBlank String targetUserId,
            String reason
    ) {
        EscalationPolicy toDomain() {
            return new EscalationPolicy(Duration.parse(thresholdIso), targetUserId, reason);
        }
    }

    public record RoutingRuleDto(
            @NotBlank String sourceStepId,
            String condition,
            boolean defaultRoute,
            @NotEmpty List<String> targetStepIds
    ) {
        RoutingRule toDomain() {
            return new RoutingRule(sourceStepId, condition, defaultRoute, targetStepIds);
        }
    }
}
