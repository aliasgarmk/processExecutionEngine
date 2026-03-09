package com.unifize.processengine.api.dto.response;

import com.unifize.processengine.model.ProcessInstance;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record InstanceResponse(
        String instanceId,
        String definitionId,
        int definitionVersion,
        UserDto initiator,
        String status,
        Set<String> activeStepIds,
        Map<String, Object> fieldValues,
        long version,
        Instant createdAt
) {
    public static InstanceResponse from(ProcessInstance i) {
        return new InstanceResponse(
                i.instanceId(),
                i.definitionId(),
                i.definitionVersion(),
                new UserDto(i.initiator().userId(), i.initiator().displayName()),
                i.status().name(),
                i.activeStepIds(),
                i.fieldValues(),
                i.version(),
                i.createdAt()
        );
    }

    public record UserDto(String userId, String displayName) {}
}
