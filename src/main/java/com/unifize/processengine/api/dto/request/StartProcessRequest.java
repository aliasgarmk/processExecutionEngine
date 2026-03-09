package com.unifize.processengine.api.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StartProcessRequest(
        @NotBlank String definitionId,
        @NotBlank String initiatorId,
        Map<String, Object> fields
) {
    public Map<String, Object> resolvedFields() {
        return fields != null ? fields : Map.of();
    }
}
