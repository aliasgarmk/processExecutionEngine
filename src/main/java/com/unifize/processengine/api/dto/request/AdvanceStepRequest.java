package com.unifize.processengine.api.dto.request;

import com.unifize.processengine.model.Action;
import com.unifize.processengine.model.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record AdvanceStepRequest(
        @NotBlank String actorId,
        @NotNull ActionType actionType,
        String reason,
        Map<String, Object> fields
) {
    public Action toAction() {
        Map<String, Object> resolvedFields = fields != null ? fields : Map.of();
        return switch (actionType) {
            case APPROVE  -> Action.approve();
            case REJECT   -> Action.reject(reason);
            case SUBMIT   -> Action.submit(resolvedFields);
            case REOPEN   -> Action.reopen(reason);
            case REASSIGN -> Action.reassign(reason);
            case ESCALATE -> Action.escalate(reason);
            case OPEN     -> Action.open();
        };
    }
}
