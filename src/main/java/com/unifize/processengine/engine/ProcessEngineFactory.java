package com.unifize.processengine.engine;

import com.unifize.processengine.support.AtomicSequenceGenerator;
import com.unifize.processengine.support.SequenceGenerator;

public final class ProcessEngineFactory {
    private ProcessEngineFactory() {
    }

    public static EngineRuntime createInMemoryRuntime() {
        ClockProvider clockProvider = new SystemClockProvider();
        SequenceGenerator sequenceGenerator = new AtomicSequenceGenerator();
        InMemoryPersistence persistence = new InMemoryPersistence();
        InMemoryEscalationScheduler escalationScheduler = new InMemoryEscalationScheduler();
        InMemoryDefinitionRegistry definitionRegistry = new InMemoryDefinitionRegistry(clockProvider);
        InMemoryAuditWriter auditWriter = new InMemoryAuditWriter(sequenceGenerator, clockProvider, persistence);
        InMemoryStateStore stateStore = new InMemoryStateStore(persistence);
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
        InMemoryUserResolver userResolver = new InMemoryUserResolver();
        DefaultStepActivator stepActivator = new DefaultStepActivator(
                escalationScheduler, clockProvider, sequenceGenerator, userResolver);

        ProcessEngine processEngine = new ProcessEngine(
                new DefaultDefinitionValidator(),
                definitionRegistry,
                new DefaultInstanceFactory(clockProvider),
                stepActivator,
                new DefaultTransitionGuard(),
                new DefaultFieldValidator(new DefaultExpressionEvaluator()),
                new DefaultRoutingRuleEvaluator(new DefaultExpressionEvaluator()),
                new DefaultParallelQuorumChecker(),
                auditWriter,
                stateStore,
                escalationScheduler,
                eventPublisher,
                clockProvider,
                userResolver
        );

        EscalationWorker escalationWorker = new EscalationWorker(processEngine, stateStore);
        return new EngineRuntime(processEngine, definitionRegistry, stateStore, auditWriter,
                escalationScheduler, eventPublisher, escalationWorker, userResolver);
    }

    /**
     * The eventPublisher field is typed as InMemoryEventPublisher (not the interface) so that
     * test code can call publishedEvents() without a cast. Production code should depend only
     * on the EventPublisher interface.
     * The userResolver is typed as InMemoryUserResolver so test code can call register().
     */
    public record EngineRuntime(
            ProcessEngine processEngine,
            DefinitionRegistry definitionRegistry,
            StateStore stateStore,
            AuditWriter auditWriter,
            EscalationScheduler escalationScheduler,
            InMemoryEventPublisher eventPublisher,
            EscalationWorker escalationWorker,
            InMemoryUserResolver userResolver
    ) {
    }
}
