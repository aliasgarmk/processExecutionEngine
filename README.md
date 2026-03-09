# Process Execution Engine

A Java 21 library that drives structured, multi-step business processes — CAPA workflows, approval chains, escalations, and parallel reviews — through a clean, LLD-aligned domain model.

A Spring Boot 3 REST API layer wraps the engine so it can be called over HTTP without any Java integration work. This is done for testing purpoose only, Ideally Rest api layer should be part of business logic service which include this engine as maven depandancy.

## Requirements

- Java 21
- Maven 3.9+

## Running the Application

### Start the server

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

Once running, the following URLs are available:

```
Swagger UI (interactive)  ->  http://localhost:8080/swagger-ui.html
OpenAPI JSON spec         ->  http://localhost:8080/v3/api-docs
```

### Postman collection (recommended)

A ready-made Postman collection is included at `postman_collection.json` in the repo root.

**Import steps:**
1. Open Postman → **Import** → select `postman_collection.json`
2. Start the server (`mvn spring-boot:run`)
3. Run the requests **in folder order**:

| Folder | What it does |
|---|---|
| 1. Users | Registers Carol and Alice |
| 2. Definitions | Loads the CAPA workflow blueprint |
| 3. Instances → **Start Process** | Starts a run; **auto-saves `instanceId`** as a collection variable |
| 4. Step: initiation | Open → Advance (Submit); auto-saves the next `stepId` |
| 5. Step: review | Open → Approve (or Reject as alternative) |
| 6. Audit | Fetches the full audit trail |

The `instanceId` is captured automatically by a test script on the "Start Process" request — every subsequent request picks it up from the collection variable without any copy-paste.

To build and run a self-contained JAR instead:

```bash
mvn package -DskipTests
java -jar target/process-execution-engine-0.1.0-SNAPSHOT.jar
```

### Build & Test (library only)

```bash
mvn compile
mvn test
```

All 6 tests pass with no external dependencies.

---

## Typical walkthrough

```bash
# 1. Register actors
curl -s -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"userId":"carol","displayName":"Carol Smith"}'

curl -s -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","displayName":"Alice Nguyen"}'

# 2. Load a definition
curl -s -X POST http://localhost:8080/api/definitions \
  -H 'Content-Type: application/json' \
  -d '{
    "definitionId":"capa","name":"CAPA Workflow","publishedBy":"admin",
    "steps":[
      {"stepId":"initiation","stepType":"TASK","assigneeRule":{"type":"INITIATOR"}},
      {"stepId":"review","stepType":"APPROVAL","assigneeRule":{"type":"USER_IDS","userIds":["alice"]}}
    ],
    "routingRules":[{"sourceStepId":"initiation","defaultRoute":true,"targetStepIds":["review"]}]
  }' | jq .

# 3. Start an instance
INSTANCE=$(curl -s -X POST http://localhost:8080/api/instances \
  -H 'Content-Type: application/json' \
  -d '{"definitionId":"capa","initiatorId":"carol","fields":{"severity":"High"}}' | jq -r .instanceId)

# 4. Open and advance the first step
curl -s -X POST http://localhost:8080/api/instances/$INSTANCE/steps/initiation/open \
  -H 'Content-Type: application/json' \
  -d '{"actorId":"carol"}'

curl -s -X POST http://localhost:8080/api/instances/$INSTANCE/steps/initiation/advance \
  -H 'Content-Type: application/json' \
  -d '{"actorId":"carol","actionType":"SUBMIT","fields":{}}' | jq .

# 5. Open and advance the review step
curl -s -X POST http://localhost:8080/api/instances/$INSTANCE/steps/review/open \
  -H 'Content-Type: application/json' \
  -d '{"actorId":"alice"}'

curl -s -X POST http://localhost:8080/api/instances/$INSTANCE/steps/review/advance \
  -H 'Content-Type: application/json' \
  -d '{"actorId":"alice","actionType":"APPROVE"}' | jq .

# 6. Read the audit trail
curl -s http://localhost:8080/api/instances/$INSTANCE/audit | jq .
```

---

## Architecture

The engine follows a strict **single-responsibility + constructor injection** design. `ProcessEngine` is the only public entry point; it owns no business logic and only sequences calls to specialist collaborators.

```
ProcessEngine
├── DefinitionValidator      validates + compiles a ProcessDefinition before registration
├── DefinitionRegistry       stores versioned definitions; pins instances to their load-time version
├── InstanceFactory          creates a new ProcessInstance from a definition and initiator
├── DefaultStepActivator     resolves assignees via UserResolver; creates PENDING StepStates
├── TransitionGuard          guards every state transition (active, authorised, in-progress, valid action)
├── FieldValidator           validates submitted field values against schemas and cross-field rules
├── RoutingRuleEvaluator     evaluates conditional routing rules; throws if no rule matches
├── ParallelQuorumChecker    tracks per-participant votes; determines when quorum is reached
├── AuditWriter              append-only audit log; persists immediately on record()
├── StateStore               atomic instance + step state persistence with optimistic locking
├── EscalationScheduler      schedules and cancels per-step escalation timers
├── EventPublisher           publishes domain events (process started/completed, step transitioned, escalation triggered)
└── UserResolver             resolves user IDs to User objects via the user directory boundary
```

### Step Lifecycle

Every step follows a three-phase lifecycle:

```
activateStep()  →  PENDING
openStep()      →  IN_PROGRESS
advanceStep()   →  COMPLETED | REJECTED | ESCALATED | SKIPPED
```

Escalation timers are started in `openStep`, not at activation, so SLA clocks begin when an actor actually opens the step.

### Key Model Types

| Type | Role |
|---|---|
| `ProcessDefinition` | Immutable blueprint — steps, routing rules, field schemas, quorum policies |
| `StepDefinition` | One node in the graph — type, assignee rule, validation, escalation policy |
| `ProcessInstance` | Immutable aggregate root — field values, active step IDs, version counter |
| `StepState` | Per-actor state record — `createdAt`, `openedAt`, `completedAt`, status |
| `Action` | Value object passed to `advanceStep` — use static factories only |
| `StepResult` | Return value of `advanceStep` — outcome, next steps, audit entry |
| `AuditEntry` | Immutable audit record — actor, from/to status, action type, sequence number |

### Action Factories

```java
Action.submit(Map<String, Object> fields)  // TASK / CHECKLIST / REVIEW steps
Action.approve()                           // APPROVAL / PARALLEL_APPROVAL / REVIEW steps
Action.reject(String reason)               // requires non-blank reason
Action.open()                              // produced internally by openStep
Action.escalate(String reason)             // produced internally by EscalationWorker
Action.reopen(String reason)
Action.reassign(String reason)
```

### Step Types

| Type | Semantics |
|---|---|
| `TASK` | Single assignee; advances with SUBMIT |
| `CHECKLIST` | Single assignee; advances with SUBMIT; field validation enforced |
| `APPROVAL` | Single approver; advances with APPROVE or REJECT |
| `REVIEW` | Reviewer may APPROVE, REJECT, or SUBMIT; routing decides consequence |
| `PARALLEL_APPROVAL` | Multiple assignees; quorum policy (ALL or MAJORITY) determines when the step completes |

---

## Using the engine as a library (no HTTP)

### 1. Wire the runtime

```java
ProcessEngineFactory.EngineRuntime runtime = ProcessEngineFactory.createInMemoryRuntime();
ProcessEngine engine = runtime.processEngine();

runtime.userResolver().register(new User("alice", "Alice Nguyen"));
runtime.userResolver().register(new User("bob", "Bob Chen"));
```

### 2. Define a process

```java
engine.loadDefinition(new ProcessDefinition(
    "capa", 0, "CAPA Workflow",
    List.of(
        new StepDefinition("initiation", StepType.TASK,
            AssigneeRule.initiator(), List.of(), List.of(), null, null, null),
        new StepDefinition("review", StepType.APPROVAL,
            AssigneeRule.users(List.of("alice")), List.of(), List.of(), null, null, null)
    ),
    List.of(new RoutingRule("initiation", null, true, List.of("review"))),
    Instant.now(), "admin"
));
```

### 3. Start, open, and advance

```java
User carol = new User("carol", "Carol Smith");
ProcessInstance instance = engine.startProcess("capa", carol, Map.of("severity", "High"));

engine.openStep(instance.instanceId(), "initiation", carol);
StepResult r = engine.advanceStep(instance.instanceId(), "initiation", Action.submit(Map.of()), carol);

User alice = new User("alice", "Alice Nguyen");
engine.openStep(instance.instanceId(), "review", alice);
StepResult final_ = engine.advanceStep(instance.instanceId(), "review", Action.approve(), alice);
System.out.println(final_.processCompleted()); // true
```

### 4. Audit trail

```java
engine.getAuditTrail(instance.instanceId())
      .forEach(e -> System.out.printf("%s: %s → %s%n",
          e.actor().displayName(), e.fromStatus(), e.toStatus()));
```

---

## Extension Points

| Interface | Swap to add |
|---|---|
| `UserResolver` | Real user directory (LDAP, REST API) |
| `StateStore` | Database-backed persistence (JPA, JDBC) |
| `AuditWriter` | Centralised audit database |
| `EventPublisher` | Message broker (Kafka, SQS, SNS) |
| `EscalationScheduler` | Distributed scheduler (Quartz, DB-backed timers) |
| `ExpressionEvaluator` | Richer expression language |

All collaborators are injected via constructor; replace any by building your own `ProcessEngine` instance with `new ProcessEngine(...)` rather than using `ProcessEngineFactory`.

## Package Layout

```
com.unifize.processengine
├── api/
│   ├── config/          Spring @Configuration — EngineRuntime, ProcessEngine, StateStore, etc. as beans
│   ├── controller/      REST controllers (Users, Definitions, Instances)
│   ├── dto/request/     Inbound JSON records with Bean Validation annotations
│   ├── dto/response/    Outbound JSON records with from(DomainType) factories
│   └── exception/       GlobalExceptionHandler — maps engine exceptions to ProblemDetail
├── engine/              interfaces + default implementations + in-memory stubs
├── model/               immutable value objects and enums
├── exception/           typed checked exceptions
└── support/             SequenceGenerator (AtomicLong-backed)
```

## Design Notes

- `ProcessInstance` is an immutable record; all mutations return new copies via `with*` methods and are guarded by an optimistic version counter.
- `FieldSchema` pre-compiles regex patterns in its compact constructor — `Pattern.compile` is paid once at definition load time, not on every `advanceStep` call.
- `AuditWriter.record()` persists immediately and is the sole write path to the audit table. `StateStore` handles only instance and step state.
- `User.SYSTEM` / `User.SYSTEM_USER_ID` is the authoritative sentinel for system-initiated transitions (escalations); `DefaultTransitionGuard` bypasses actor authorisation checks for this identity.
- The Spring Boot API layer is a thin adapter — no engine logic lives there. All HTTP-to-domain translation happens in the DTO `toDomain()` / `from()` methods.
