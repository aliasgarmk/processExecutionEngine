package com.unifize.processengine.api.config;

import com.unifize.processengine.engine.DefinitionRegistry;
import com.unifize.processengine.engine.InMemoryUserResolver;
import com.unifize.processengine.engine.ProcessEngine;
import com.unifize.processengine.engine.ProcessEngineFactory;
import com.unifize.processengine.engine.StateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public ProcessEngineFactory.EngineRuntime engineRuntime() {
        return ProcessEngineFactory.createInMemoryRuntime();
    }

    @Bean
    public ProcessEngine processEngine(ProcessEngineFactory.EngineRuntime runtime) {
        return runtime.processEngine();
    }

    @Bean
    public InMemoryUserResolver userResolver(ProcessEngineFactory.EngineRuntime runtime) {
        return runtime.userResolver();
    }

    @Bean
    public DefinitionRegistry definitionRegistry(ProcessEngineFactory.EngineRuntime runtime) {
        return runtime.definitionRegistry();
    }

    @Bean
    public StateStore stateStore(ProcessEngineFactory.EngineRuntime runtime) {
        return runtime.stateStore();
    }
}
