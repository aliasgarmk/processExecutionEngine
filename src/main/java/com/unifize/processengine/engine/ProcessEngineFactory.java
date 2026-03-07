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
        InMemoryAuditWriter auditWriter = new InMemoryAuditWriter(sequenceGenerator, persistence);
        InMemoryStateStore stateStore = new InMemoryStateStore(persistence);
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();

        ProcessEngine processEngine = new ProcessEngine(
                new DefaultDefinitionValidator(),
                definitionRegistry,
                new DefaultInstanceFactory(clockProvider),
                new DefaultStepActivator(escalationScheduler, clockProvider, sequenceGenerator),
                new DefaultTransitionGuard(),
                new DefaultFieldValidator(new DefaultExpressionEvaluator()),
                new DefaultRoutingRuleEvaluator(new DefaultExpressionEvaluator()),
                new DefaultParallelQuorumChecker(),
                auditWriter,
                stateStore,
                escalationScheduler,
                eventPublisher,
                clockProvider
        );

        EscalationWorker escalationWorker = new EscalationWorker(processEngine, stateStore);
        return new EngineRuntime(processEngine, definitionRegistry, stateStore, auditWriter, escalationScheduler, eventPublisher, escalationWorker);
    }

    public record EngineRuntime(
            ProcessEngine processEngine,
            DefinitionRegistry definitionRegistry,
            StateStore stateStore,
            AuditWriter auditWriter,
            EscalationScheduler escalationScheduler,
            EventPublisher eventPublisher,
            EscalationWorker escalationWorker
    ) {
    }
}
