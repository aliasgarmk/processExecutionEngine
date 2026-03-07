package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessDefinition;

public interface DefinitionRegistry {
    ProcessDefinition register(ProcessDefinition definition);

    ProcessDefinition resolveLatest(String definitionId);

    ProcessDefinition resolveVersion(String definitionId, int version);

    void evictFromCache(String definitionId);
}
