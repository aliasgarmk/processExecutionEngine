package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessDefinition;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class InMemoryDefinitionRegistry implements DefinitionRegistry {
    private final Map<String, NavigableMap<Integer, ProcessDefinition>> definitions = new ConcurrentHashMap<>();
    private final ClockProvider clockProvider;

    public InMemoryDefinitionRegistry(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    @Override
    public ProcessDefinition register(ProcessDefinition definition) {
        NavigableMap<Integer, ProcessDefinition> versions = definitions.computeIfAbsent(
                definition.definitionId(),
                ignored -> new ConcurrentSkipListMap<>()
        );
        int nextVersion = versions.isEmpty() ? 1 : versions.lastKey() + 1;
        ProcessDefinition persisted = new ProcessDefinition(
                definition.definitionId(),
                nextVersion,
                definition.name(),
                definition.steps(),
                definition.routingRules(),
                definition.publishedAt() == null ? clockProvider.now() : definition.publishedAt(),
                definition.publishedBy()
        );
        versions.put(nextVersion, persisted);
        return persisted;
    }

    @Override
    public ProcessDefinition resolveLatest(String definitionId) {
        NavigableMap<Integer, ProcessDefinition> versions = definitions.get(definitionId);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("No definition found for id: " + definitionId);
        }
        return versions.lastEntry().getValue();
    }

    @Override
    public ProcessDefinition resolveVersion(String definitionId, int version) {
        NavigableMap<Integer, ProcessDefinition> versions = definitions.get(definitionId);
        if (versions == null || !versions.containsKey(version)) {
            throw new IllegalArgumentException("Definition version not found: " + definitionId + ":" + version);
        }
        return versions.get(version);
    }

    @Override
    public void evictFromCache(String definitionId) {
        // No-op for in-memory registry.
    }
}
