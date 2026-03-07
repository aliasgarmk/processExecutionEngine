# Process Execution Engine Implementation Plan

## 1. Scope

This plan is derived from:

- `Process Execution Engine PRD.docx`
- `Process Execution Engine HLD.docx`
- `Process Execution Engine LLD.docx`

It covers the first production implementation of the configurable process execution engine, including:

- Definition registration and validation
- Process instance lifecycle management
- Step activation and transition handling
- Conditional routing and parallel approvals
- Immutable audit trail
- Escalation scheduling and worker flow
- Domain event publishing
- Operational readiness, testing, and rollout

This plan assumes the implementation follows the Java-oriented LLD, uses a relational database for atomic writes, and uses a durable message queue for delayed escalations and downstream notifications.

## 2. Delivery Principles

- Ship the smallest compliant core first: definition load, start process, advance step, audit log.
- Keep state mutation and audit insert in one transaction from day one.
- Pin process instances to definition version at creation time and never mutate that binding.
- Treat optimistic locking, UTC timestamps, and monotonic audit sequencing as non-negotiable platform constraints.
- Keep notification and reporting concerns out of the critical execution path.

## 3. Target Deliverables

### Phase 1 deliverable

A production-usable MVP that supports:

- Versioned process definitions
- Standard sequential steps
- Validation rules and conditional routing
- Parallel approvals with `ALL` and `MAJORITY` quorum
- Escalation scheduling and processing
- Immutable per-instance audit trail
- Core APIs for load definition, start process, advance step, get audit trail

### Fast-follow deliverables

- Cross-process dependencies
- Real-time dashboard and notifications UX
- Delegation and out-of-office handling
- Audit export
- Process analytics

## 4. Workstreams

### A. Domain model and contracts

Implement the value objects, enums, aggregates, and exception catalogue defined in the LLD:

- `ProcessDefinition`, `StepDefinition`, `ProcessInstance`, `StepState`, `AuditEntry`
- `StepType`, `StepStatus`, `ActionType`, `InstanceStatus`
- Routing, validation, assignee, quorum, and escalation policy types
- Checked and unchecked exceptions from section 22

Exit criteria:

- Domain model compiles cleanly
- Mutability boundaries match the LLD
- Serialization contracts are stable for API and persistence layers

### B. Persistence and transactional state store

Build the database model and repositories behind `StateStore`, `DefinitionRegistry`, and `AuditWriter`.

Core schema areas:

- `process_definitions`
- `process_definition_steps`
- `process_definition_routing_rules`
- `process_instances`
- `step_states`
- `audit_entries`

Required guarantees:

- Atomic write of instance update, step state changes, and audit entry
- Optimistic locking on `process_instances`
- Append-only audit table with no update/delete path in application code
- UTC timestamps everywhere
- Monotonic sequence number on audit entries

Exit criteria:

- Concurrency conflict returns deterministic `OptimisticLockException`
- Audit rows cannot be mutated through normal application roles
- Per-instance audit trail is ordered by sequence number

### C. Definition management

Implement:

- `DefinitionValidator`
- `DefinitionRegistry`
- `ProcessEngine.loadDefinition`

Validation rules to enforce in MVP:

- No duplicate step IDs
- All routing targets exist
- Every non-terminal step has a guaranteed exit path
- No cyclic routing graph
- Parallel steps must declare quorum policy
- Routing precedence is declaration-order based

Exit criteria:

- Invalid definitions are rejected before persistence
- New definition versions register cleanly
- `resolveLatest` and `resolveVersion` behave exactly as specified

### D. Core execution engine

Implement the orchestration path in `ProcessEngine` and the supporting classes:

- `InstanceFactory`
- `TransitionGuard`
- `FieldValidator`
- `RoutingRuleEvaluator`
- `StepActivator`
- `ParallelQuorumChecker`

Execution scenarios to support:

- Start process from latest definition version
- Advance standard step with validation
- Evaluate first-match routing with default fallback
- Activate next step or multiple next steps
- Handle parallel approvals independently per participant
- Route on rejection
- Complete process when terminal state is reached

Exit criteria:

- State transitions are legal-only and auditable
- Actor authorization is enforced before mutation
- Replayed scenario tests match PRD CAPA flow semantics

### E. Audit trail and compliance controls

Implement:

- `AuditWriter`
- Per-instance trail read path
- Date-range read path stub or reporting read model adapter

Always log:

- Process start
- Step transitions
- Field submissions/updates
- Routing decisions
- Escalations
- Process completion, cancellation, reopen

Compliance controls:

- Server-assigned UTC timestamps only
- Monotonic sequence generator
- Immutable actor IDs
- No delete or update API for audit records

Exit criteria:

- Auditor-style reconstruction of a process instance is possible from audit data alone
- Sequence ordering remains correct even if wall clock changes

### F. Escalation and async infrastructure

Implement:

- `EscalationScheduler`
- `EscalationWorker`
- Domain event payloads
- `EventPublisher`

Behaviour required in MVP:

- Schedule escalation when an in-progress step has policy
- Cancel best-effort when step completes early
- Worker validates instance is active and step still open
- Worker invokes engine with `SYSTEM_USER` and `ActionType.ESCALATE`
- Publish post-commit domain events for notifications and downstream consumers

Exit criteria:

- Duplicate or delayed escalation messages are safely ignored
- Scheduler outages do not corrupt main execution flow
- Notification/event publish failures do not fail the main transaction

### G. API surface

Expose internal or external APIs for:

- Load definition
- Start process
- Advance step
- Get audit trail

API responsibilities:

- Authentication and caller identity resolution
- Request schema validation
- Error mapping from engine exceptions
- Read authorization on audit trail

Exit criteria:

- APIs are idempotent where appropriate
- Engine exceptions map to stable API error codes
- Contract tests cover happy path and business-rule failures

### H. Observability and operations

Add:

- Structured logs with instance ID, step ID, definition ID/version, actor ID
- Metrics for start, advance, conflicts, escalations, queue lag, validation failures
- Alerts for audit write failures, queue backlog, optimistic lock spikes
- Runbooks for stuck escalations and replay-safe worker recovery

Exit criteria:

- Operators can identify failed transitions and backlog causes without DB forensics
- Alerts exist for the risks called out in the HLD

## 5. Recommended Delivery Sequence

### Phase 0. Foundation and decisions

Duration: 3 to 5 days

- Confirm runtime stack, relational database, queue technology, and transaction strategy
- Finalize JSON/API contract for definition input
- Define expression language for validation and routing
- Define assignee rule resolution contract with the identity/org system
- Produce schema and message contract review

Milestone:

- Architecture sign-off on persistence, queue, and expression-evaluation approach

### Phase 1. Domain and persistence backbone

Duration: 1 to 1.5 weeks

- Implement domain entities and exceptions
- Create database schema and migrations
- Implement `StateStore`, `AuditWriter`, and `DefinitionRegistry`
- Implement audit sequence generation and optimistic locking

Milestone:

- Can persist and load definitions, instances, step states, and audit entries reliably

### Phase 2. Definition load and validation

Duration: 4 to 6 days

- Implement `DefinitionValidator`
- Implement `ProcessEngine.loadDefinition`
- Add graph validation and routing validation tests

Milestone:

- Invalid process configurations are rejected before any execution can start

### Phase 3. Start process and single-step execution

Duration: 1 week

- Implement `InstanceFactory`, `TransitionGuard`, `FieldValidator`, `StepActivator`
- Implement `ProcessEngine.startProcess`
- Implement standard `advanceStep` for non-parallel steps
- Add audit emission on every mutation

Milestone:

- Sequential workflow execution works end to end

### Phase 4. Routing and parallel approvals

Duration: 1 week

- Implement `RoutingRuleEvaluator`
- Implement `ParallelQuorumChecker`
- Support `ALL` and `MAJORITY`
- Support rejection path and remaining-participant tracking

Milestone:

- CAPA-style review step works with multiple approvers and deterministic routing

### Phase 5. Escalations and scheduled reopen

Duration: 1 week

- Implement `EscalationScheduler` and delayed message contract
- Implement `EscalationWorker`
- Support system-triggered escalation flow
- Support scheduled effectiveness check flow

Milestone:

- Overdue and delayed steps are advanced or re-opened safely and idempotently

### Phase 6. API hardening and operational controls

Duration: 4 to 6 days

- Finalize API endpoints
- Add observability, metrics, alerts, and retry policies
- Add access control on audit reads
- Add performance and soak tests for conflict-heavy scenarios

Milestone:

- Service is deployable to a controlled pre-production environment

### Phase 7. UAT, compliance validation, and rollout

Duration: 1 week

- Run end-to-end UAT using representative regulated workflows
- Validate audit reconstruction against compliance expectations
- Execute concurrency, escalation, and version-pinning drills
- Prepare production runbook and rollback plan

Milestone:

- Production readiness sign-off

## 6. Testing Strategy

### Unit tests

- Definition graph validation
- Field validation and cross-field rules
- Routing precedence and fallback routing
- Quorum logic
- Assignee resolution
- Audit ordering and sequence generation

### Integration tests

- Start process and advance step across transaction boundaries
- Optimistic lock conflict on concurrent approval
- Audit entry written atomically with state mutation
- Definition version pinning across mid-flight definition publish
- Escalation event consumed after step already closed

### End-to-end tests

- Full CAPA happy path
- Rejection loop back to investigation
- Parallel approval with majority quorum
- Escalation to manager after missed deadline
- Effectiveness check reopening prior investigation flow

### Non-functional tests

- High-concurrency approvals on same instance
- Queue outage and worker recovery
- Audit volume growth and trail query performance
- Long-running instances spanning clock corrections and delayed jobs

## 7. Key Risks and Mandatory Controls

### Must be solved before production

- Audit atomicity: state and audit must commit together
- Version pinning: in-flight instances must never pick up new definition semantics
- Optimistic locking: concurrent step transitions must not double-commit
- Idempotent escalation handling: delayed or duplicate queue deliveries must be safe
- Definition validation completeness: malformed graphs must not reach runtime

### Can be deferred, but should be designed now

- Reporting read model for date-range audit exports
- Notification fanout implementation details
- Hash-chained audit enhancement
- Cross-process dependency engine
- Delegation and out-of-office rules

## 8. Team and Ownership Suggestion

- Backend engineer 1: domain model, engine orchestration, validators
- Backend engineer 2: persistence, audit, concurrency, migrations
- Platform engineer: queue, worker, observability, deployment
- Frontend/API engineer: definition ingestion contract, workflow APIs, error mapping
- QA/SDET: concurrency, workflow, escalation, and audit reconstruction suites
- Product/compliance reviewer: audit expectations, CAPA scenario validation

## 9. Definition of Done for MVP

The MVP is complete when all of the following are true:

- An admin can publish a valid versioned process definition
- A user can start a process instance pinned to the latest published version
- Assigned users can advance steps subject to field validation and authorization
- Parallel approvals support `ALL` and `MAJORITY`
- Conditional routing is deterministic and validated
- Overdue steps escalate asynchronously with `SYSTEM` audit attribution
- Every state mutation produces an immutable audit entry
- Concurrent conflicting actions are rejected safely
- Closed instances do not process stale scheduled events
- The CAPA reference workflow executes end to end in automated tests

## 10. Suggested Immediate Next Actions

- Finalize the data model and transaction boundaries first
- Implement the write path before notifications, reporting, or dashboards
- Build the CAPA workflow as the reference acceptance suite
- Treat fast-follow items as separate epics once the engine core is stable
