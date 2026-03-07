package com.unifize.processengine.engine;

import com.unifize.processengine.model.InstanceStatus;
import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.User;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class DefaultInstanceFactory implements InstanceFactory {
    private final ClockProvider clockProvider;

    public DefaultInstanceFactory(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    @Override
    public ProcessInstance createInstance(ProcessDefinition definition, User initiator, Map<String, Object> initialFields) {
        return new ProcessInstance(
                UUID.randomUUID().toString(),
                definition.definitionId(),
                definition.version(),
                initiator,
                initialFields,
                new HashSet<>(),
                InstanceStatus.ACTIVE,
                1L,
                clockProvider.now()
        );
    }
}
