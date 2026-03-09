# Process Execution Engine — Code Review Notes

**Reviewed against:** `Documents/Process Execution Engine LLD.pdf`
**Scope:** All classes under `src/main/java/com/unifize/processengine/`
**Status:** All P0, P1, and P2 issues implemented. All 6 tests pass.

---

## Summary

The implementation is structurally sound and covers the majority of the LLD. The package layout, interface/implementation split, in-memory persistence, and escalation worker are all present. However, there are **9 critical issues (P0)**, **8 significant issues (P1)**, and **4 design-level issues (P2)** that must be addressed before this is production-ready.

The single most impactful defect is the **missing `Action` value object**. The LLD explicitly defines `Action` as a first-class domain type and explicitly warns against dissolving it into `ActionType + Map`. That dissolution is exactly what the implementation does, causing a cascade of interface contract violations across `ProcessEngine`, `FieldValidator`, and `AuditWriter`.

---

## Critical Issues — P0 (Must not ship)

### C1 — `Action` value object is missing; `advanceStep` interface contract is broken

**File:** [ProcessEngine.java](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L112)

**What the LLD says:**
```
advanceStep(String instanceId, String stepId, Action action, User actor) : StepResult
```
The LLD includes a dedicated Alignment Note:
> "advanceStep now accepts Action as a single value object — matching the original assignment's interface contract. The previous version split Action into ActionType + a loose Map parameter, which dissolved the type."

**What the code does:**
```java
public StepResult advanceStep(String instanceId, String stepId,
    ActionType action, User actor, Map<String, Object> fields)
```

**Why this matters:** The LLD treats dissolving `Action` into separate parameters as an explicit regression. `Action` is a domain concept that encapsulates *what an actor is doing and what they submitted*. Without it, callers have no compile-time contract enforcing that a REJECT action must include a reason, or that a SUBMIT action carries fields. That constraint disappears into runtime checks that may not exist.

**Fix:** Create the `Action` class from LLD Section 3 with static factories (`Action.approve()`, `Action.reject(reason)`, `Action.submit(fields)`, `Action.escalate(reason)`, `Action.reopen(reason)`) and update `advanceStep`, `FieldValidator.validate`, and `AuditWriter.record` accordingly.

---

### C2 — `AuditWriter.record()` does not persist — audit entries can be silently dropped

**File:** [InMemoryAuditWriter.java](src/main/java/com/unifize/processengine/engine/InMemoryAuditWriter.java#L25)

**What the LLD says:**
> "Persists immediately. Returns the created entry. This is the only write path to the audit table."

**What the code does:** `InMemoryAuditWriter.record()` constructs an `AuditEntry` and returns it without storing it anywhere. The caller (`ProcessEngine`) must pass the entry to `StateStore.updateInstance()` for it to be stored. If any call path fails to do this — or if future code calls `record()` without forwarding the result — the audit entry is silently lost.

**Why this matters:** The LLD's invariant is that calling `record()` is sufficient. The current design makes persistence an opt-in afterthought for the caller, violating append-only guarantees and the "this is the only write path" contract.

**Fix:** `AuditWriter.record()` must write directly to the audit store. Remove the audit list threading through `StateStore`. StateStore handles instance + step state mutations; AuditWriter handles audit writes — these must be independent.

---

### C3 — `NoRoutingMatchException` is never thrown — stalled processes fail silently

**File:** [DefaultRoutingRuleEvaluator.java](src/main/java/com/unifize/processengine/engine/DefaultRoutingRuleEvaluator.java#L37)

**What the LLD says:**
> "Throws NoRoutingMatchException if no rule matches and no default exists — this indicates a malformed definition that slipped through validation."

**What the code does:**
```java
return List.of();  // silently returns empty list
```

**Why this matters:** When no routing rule matches, `ProcessEngine.advanceStep` receives an empty `nextStepIds` list and calls `instance.completeIfNoActiveSteps()`, marking the instance COMPLETED. A process that should have continued instead closes silently with no error, no audit trace explaining why, and no way for the operator to distinguish a legitimate completion from a routing failure.

**Fix:** Throw `NoRoutingMatchException` when `rules` is non-empty (i.e., the step has declared rules but none matched and there is no default). A completely terminal step with no routing rules at all is valid — that case should return `List.of()`.

---

### C4 — `ProcessInstance` exposes public mutation methods — aggregate root invariant violated

**File:** [ProcessInstance.java](src/main/java/com/unifize/processengine/model/ProcessInstance.java#L91-L108)

**What the LLD says:**
> "Exposes only state-reading methods — all mutations happen through the engine, never directly."

**What the code does:** `putFieldValues()`, `setActiveStepIds()`, `completeIfNoActiveSteps()`, and `incrementVersion()` are all public. Any class with a reference to a `ProcessInstance` can mutate it, bypassing every guard the engine provides.

**Why this matters:** The aggregate root pattern only works if mutations go through the engine. Public setters on the root mean a test helper, a downstream service, or future code can corrupt state without any guard firing.

**Fix:** Make mutation methods package-private (they are only needed from within the engine package). Alternatively, move the mutation logic into the engine itself and make `ProcessInstance` fully immutable by returning new copies. The `copy()` method already exists — use it.

---

### C5 — `InMemoryPersistence` exposes raw mutable fields — synchronisation boundary leaks

**File:** [InMemoryPersistence.java](src/main/java/com/unifize/processengine/engine/InMemoryPersistence.java#L13-L15)

**What the code does:**
```java
final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
final Map<String, List<StepState>> stepStatesByInstance = new ConcurrentHashMap<>();
final Map<String, List<AuditEntry>> auditEntriesByInstance = new ConcurrentHashMap<>();
```

`InMemoryStateStore.saveInstance()` accesses `persistence.instances` directly from outside the class, bypassing any encapsulation. The `synchronized(persistence)` block in `InMemoryStateStore` only protects the store's own operations — nothing prevents another class from reading or writing these maps directly.

**Why this matters:** The `ArrayList` returned by `stepStates()` is not thread-safe. `loadStepStates()` and `loadStepStatesForStep()` read it without synchronisation while `updateInstance()` writes under `synchronized(persistence)`. This is a classic read/write race condition.

**Fix:** Make all three maps `private`. Expose only method-level accessors. Move `stepStates()` and `auditEntries()` to synchronised methods, or switch the inner lists to `CopyOnWriteArrayList`.

---

### C6 — `StepResult` field names and content diverge from LLD

**File:** [StepResult.java](src/main/java/com/unifize/processengine/model/StepResult.java)

| LLD field | LLD type | Code field | Code type |
|---|---|---|---|
| `completedStepId` | `String` | `stepId` | `String` |
| `outcomeStatus` | `StepStatus` | `resultingStatus` | `StepStatus` |
| `auditEntry` | `AuditEntry` | *(missing)* | — |

**Why this matters:** `StepResult` is the public return type of the engine's primary method. Callers that read the LLD to understand the contract will look for `completedStepId` and `outcomeStatus`. Incorrect naming creates confusion. The missing `auditEntry` field means callers must do a separate query to get the audit record for the action they just performed, which the LLD explicitly eliminates.

**Fix:** Rename fields to match the LLD. Add `auditEntry` field once C2 is resolved (AuditWriter then returns a persisted entry that can be included here).

---

### C7 — Magic string `"SYSTEM"` used as sentinel in multiple places

**Files:**
- [ProcessEngine.java:225](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L225)
- [DefaultTransitionGuard.java:36](src/main/java/com/unifize/processengine/engine/DefaultTransitionGuard.java#L36)

**What the code does:**
```java
boolean systemActor = "SYSTEM".equals(actorUserId);
if ("SYSTEM".equals(actorId)) { return; }
```

**Why this matters:** A magic string in two classes means a typo in either one silently breaks system-initiated escalations. The guard in `DefaultTransitionGuard` that bypasses authorisation for SYSTEM is a security-relevant check — it must be authoritative and centralised.

**Fix:** Define `public static final String SYSTEM_USER_ID = "SYSTEM"` in the `User` class or in `AuditWriter`. Use it everywhere.

---

### C8 — `EscalationWorker.validateEscalationIsStillApplicable()` and `processEscalation()` are public

**File:** [EscalationWorker.java](src/main/java/com/unifize/processengine/engine/EscalationWorker.java#L34-L58)

**What the LLD says:** These are internal steps called from `handleEscalationEvent`. They are implementation details, not a contract.

**Why this matters:** Making them public invites callers to short-circuit `handleEscalationEvent` and call `processEscalation` directly — skipping the idempotency check that prevents double-escalation. The LLD names them as internal methods precisely because the idempotency guard in `validateEscalationIsStillApplicable` must always precede `processEscalation`.

**Fix:** Make both methods `private`.

---

### C9 — `EscalationWorker` depends on `InMemoryAuditWriter.SYSTEM_USER` — infrastructure leak into domain

**File:** [EscalationWorker.java:56](src/main/java/com/unifize/processengine/engine/EscalationWorker.java#L56)

```java
InMemoryAuditWriter.SYSTEM_USER,
```

**Why this matters:** `EscalationWorker` is a domain-level consumer. It should not import an in-memory infrastructure class. This creates a compile-time coupling: swapping `InMemoryAuditWriter` for a database-backed implementation requires modifying `EscalationWorker`. The coupling is invisible until the first real infrastructure swap.

**Fix:** Move the `SYSTEM_USER` constant to the `User` class or to the `AuditWriter` interface as a default. Both are neutral locations with no infrastructure dependency.

---

## Significant Issues — P1 (Should not ship without addressing)

### S1 — `PENDING` status is never used — step activation design skips a lifecycle state

**File:** [DefaultStepActivator.java:37](src/main/java/com/unifize/processengine/engine/DefaultStepActivator.java#L37)

The LLD defines `PENDING = "created, not yet started"` and `IN_PROGRESS = "actor has opened the step"` as distinct states. Every step is created directly as `IN_PROGRESS`. The PENDING state exists in the enum but is unreachable in the current implementation.

**Why this matters:** `PENDING` is not a cosmetic distinction. It matters for SLA tracking (time-to-open vs. time-to-complete), for audit completeness, and for the `getRemainingParticipants()` query which correctly includes `PENDING` states. Creating steps as `IN_PROGRESS` immediately means there is no record of when an actor actually opened their step.

**Fix:** `activateStep` should create `StepState` with `PENDING`. A separate `openStep` call (or the first `advanceStep` targeting that step) transitions it to `IN_PROGRESS` and records that timestamp.

---

### S2 — `loadDefinition()` return type deviates from the LLD contract

**File:** [ProcessEngine.java:75](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L75)

LLD: `loadDefinition(ProcessDefinition def) : void`
Code: returns `ProcessDefinition`

**Why this matters:** The LLD's contract is void — callers do not get a return value. Returning the registered definition couples callers to knowing that the registered version may differ from the one submitted (version number is auto-assigned). This leaks registry internals. Callers who want the registered version should call `resolveLatest()` explicitly.

**Fix:** Change return type to `void`. Callers that need the assigned version call `definitionRegistry.resolveLatest(definitionId)`.

---

### S3 — `REVIEW` step type has no dedicated action validation branch

**File:** [DefaultTransitionGuard.java:61](src/main/java/com/unifize/processengine/engine/DefaultTransitionGuard.java#L61)

The LLD includes `REVIEW` in `StepType` with distinct semantics: "a rejection by the reviewer routes back with comments but does not hard-block the process." The current guard treats `REVIEW` as a task-like step (only allows `SUBMIT` or `REOPEN`). That means an actor on a REVIEW step cannot call `REJECT` even though the LLD says REVIEW supports rejection (with the routing rule deciding the consequence).

**Fix:** Add a dedicated branch for `REVIEW` that allows `APPROVE`, `REJECT`, and `SUBMIT`.

---

### S4 — `StateStore.saveInstance()` signature deviates from LLD

**File:** [StateStore.java:13](src/main/java/com/unifize/processengine/engine/StateStore.java#L13)

LLD: `saveInstance(ProcessInstance instance) : void`
Code: `saveInstance(ProcessInstance instance, List<StepState> initialStepStates, List<AuditEntry> auditEntries)`

The LLD places the responsibility for what gets written in `updateInstance`, not in `saveInstance`. Additionally, `updateInstance` per the LLD takes a single `AuditEntry`, not `List<AuditEntry>`. These deviations result from AuditWriter not persisting its own records (C2), forcing the store to take on audit responsibility it shouldn't own.

**Fix:** Resolving C2 (AuditWriter persists immediately) naturally restores `saveInstance` to the LLD's signature. Then `updateInstance` takes a single `AuditEntry` as the LLD specifies.

---

### S5 — Regex patterns compiled on every validation call

**File:** [DefaultFieldValidator.java:49](src/main/java/com/unifize/processengine/engine/DefaultFieldValidator.java#L49)

```java
if (!Pattern.compile(schema.regex()).matcher(stringValue).matches()) {
```

`Pattern.compile()` is called inside a loop on every `validate()` invocation. Compiled patterns are expensive to create. `validate()` is called on every `advanceStep` — for a busy process engine, this is a hot path.

**Fix:** Compile patterns once at definition registration time (in `DefinitionValidator` or `DefinitionRegistry`) and cache them. The `FieldSchema` record can carry a pre-compiled `Pattern` instead of a raw regex string, or a companion cache can map schema name → Pattern.

---

### S6 — `AuditWriter.getEntriesByDateRange()` defined in LLD but missing entirely

**What the LLD says:**
> `getEntriesByDateRange(Instant from, Instant to) : List<AuditEntry>` — returns all AuditEntries across all instances in the given range. Used for compliance reports.

Neither the `AuditWriter` interface nor `InMemoryAuditWriter` define this method.

**Fix:** Add the method to the interface and implement it in `InMemoryAuditWriter` using a stream filter over all entries across all instances.

---

### S7 — Escalation event creates `new User(assignedTo, assignedTo)` — display name is wrong

**File:** [ProcessEngine.java:195](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L195)

```java
eventPublisher.publishEscalationTriggered(
    activated.getFirst(),
    new User(activated.getFirst().assignedTo(), activated.getFirst().assignedTo())
);
```

The `User` constructor receives the user ID as both the ID and the display name. Any consumer of the `EscalationTriggered` event that displays a human-readable name will show the raw UUID or user ID string instead.

**Fix:** The `User` for the escalation target should be resolved through a user directory or carried in the `EscalationPolicy`. Do not construct `User` from just an ID.

---

### S8 — Concurrent read without synchronisation in `InMemoryStateStore`

**File:** [InMemoryStateStore.java:72-83](src/main/java/com/unifize/processengine/engine/InMemoryStateStore.java#L72)

`loadStepStates()` and `loadStepStatesForStep()` iterate over an `ArrayList` without synchronisation. `updateInstance()` writes to that same list under `synchronized(persistence)`. A concurrent read during a write can produce a `ConcurrentModificationException` or an inconsistent view.

**Fix:** Either hold `synchronized(persistence)` for reads as well, or switch the inner lists to `CopyOnWriteArrayList`. Given the read-heavy usage (validation, quorum checks), `CopyOnWriteArrayList` is appropriate.

---

## Design-Level Issues — P2 (Raises team bar)

### D1 — `toStepStatus()` and `mergeParticipantStates()` are business logic inside ProcessEngine

**File:** [ProcessEngine.java:235-259](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L235)

The LLD is explicit: ProcessEngine "owns no business logic. Its only job is to sequence calls to specialist collaborators."

`toStepStatus()` (ActionType → StepStatus mapping) is a domain rule. It belongs in `TransitionGuard` or a dedicated domain mapper.
`mergeParticipantStates()` is quorum state management. It belongs in `ParallelQuorumChecker`.

**Fix:** Move `toStepStatus()` to `TransitionGuard` as `resolveResultingStatus(ActionType) : StepStatus`. Move `mergeParticipantStates()` to `ParallelQuorumChecker`.

---

### D2 — `ClockProvider` appears in `ProcessEngine` constructor but not in the LLD

**File:** [ProcessEngine.java:43-44](src/main/java/com/unifize/processengine/engine/ProcessEngine.java#L43)

The LLD's constructor dependency list for `ProcessEngine` does not include `ClockProvider`. The clock is used in `startProcess` to timestamp audit entries. But since AuditWriter is responsible for assigning timestamps (it calls `clockProvider.now()` in its own scope in a correct design), `ProcessEngine` should not need to know about the clock at all.

**Fix:** After resolving C2, the timestamp responsibility moves back to `AuditWriter`. Remove `ClockProvider` from `ProcessEngine`'s constructor.

---

### D3 — `publishedEvents()` method on `EventPublisher` interface — test concern on production type

**File:** [EventPublisher.java:19](src/main/java/com/unifize/processengine/engine/EventPublisher.java#L19)

`publishedEvents()` is a testing/observation helper — it only makes sense on `InMemoryEventPublisher`. Putting it on the interface forces any real implementation (Kafka, SNS, etc.) to implement a method that has no meaning outside of tests.

**Fix:** Remove `publishedEvents()` from the interface. Add a `TestableEventPublisher` sub-interface or expose it only on `InMemoryEventPublisher` directly, cast to that type in tests.

---

### D4 — `DefaultExpressionEvaluator` throws `IllegalArgumentException` on unknown clauses — unchecked exception leaks to callers

**File:** [DefaultExpressionEvaluator.java:56](src/main/java/com/unifize/processengine/engine/DefaultExpressionEvaluator.java#L56)

```java
throw new IllegalArgumentException("Unsupported expression clause: " + clause);
```

The LLD notes the evaluator must use a "sandboxed expression evaluator to prevent injection." An `IllegalArgumentException` thrown from inside routing evaluation will propagate unhandled through `DefaultRoutingRuleEvaluator` and surface in `ProcessEngine.advanceStep` as an unexpected unchecked exception — one that callers don't know to handle and that provides an expression string in its message that may expose internal definition details.

**Fix:** Define a typed `ExpressionEvaluationException` (unchecked is fine, but named). Catch it in `RoutingRuleEvaluator` and wrap it with the routing rule context for diagnosability.

---

## LLD Alignment Checklist

| LLD Section | Class | Pre-fix Status | Post-fix Status |
|---|---|---|---|
| §3 Action (value object) | Action.java | **Missing** | Fixed — created with static factories and `resolvedStatus()` |
| §3 StepResult fields | StepResult | Field names wrong, auditEntry absent | Fixed — `completedStepId`, `outcomeStatus`, `auditEntry` |
| §4 ProcessDefinition | ProcessDefinition | Aligned | Aligned |
| §5 StepDefinition | StepDefinition | Aligned | Aligned |
| §6 ProcessInstance | ProcessInstance | Public mutations violate contract | Fixed — immutable record with `with*` methods |
| §7 StepState | StepState | Aligned | Aligned |
| §8 ProcessEngine.advanceStep signature | ProcessEngine | Wrong signature | Fixed — `(instanceId, stepId, Action, User)` |
| §8 ProcessEngine.loadDefinition return type | ProcessEngine | Returns value | Fixed — returns `void` |
| §9 DefinitionValidator | DefaultDefinitionValidator | Aligned | Aligned |
| §10 DefinitionRegistry | InMemoryDefinitionRegistry | Aligned | Aligned |
| §11 InstanceFactory | DefaultInstanceFactory | Aligned | Aligned |
| §12 TransitionGuard | DefaultTransitionGuard | REVIEW missing, magic string | Fixed — REVIEW branch, `User.SYSTEM_USER_ID` |
| §13 FieldValidator.validate(Action) | DefaultFieldValidator | Takes Map not Action | Fixed — takes `Action` |
| §14 RoutingRuleEvaluator | DefaultRoutingRuleEvaluator | Exception never thrown | Fixed — throws `NoRoutingMatchException` |
| §15 StepActivator | DefaultStepActivator | PENDING state skipped | Fixed — steps activate as PENDING; `openStep` transitions to IN_PROGRESS |
| §16 ParallelQuorumChecker | DefaultParallelQuorumChecker | Aligned | Fixed — added `mergeUpdatedState` |
| §17 AuditWriter persists immediately | InMemoryAuditWriter | Does not persist | Fixed — persists on `record()` |
| §17 AuditWriter.getEntriesByDateRange | AuditWriter interface | Missing | Fixed — added to interface and implementation |
| §18 StateStore signatures | StateStore / InMemoryStateStore | saveInstance signature wrong | Fixed — audit entries removed from signatures |
| §18 StateStore concurrent reads | InMemoryStateStore | Unguarded reads | Fixed — switched to `CopyOnWriteArrayList` |
| §19 EscalationScheduler | InMemoryEscalationScheduler | Aligned | Aligned |
| §20 EscalationWorker internal methods | EscalationWorker | Methods are public | Fixed — `private` |
| §20 EscalationWorker SYSTEM_USER | EscalationWorker | Infrastructure dependency | Fixed — uses `User.SYSTEM` |
| §21 EventPublisher | InMemoryEventPublisher | publishedEvents on interface | Fixed — moved to class only |
| §3 StepType REVIEW | StepType enum | Missing | Fixed — added `REVIEW` value |
| §— ExpressionEvaluator typed exception | DefaultExpressionEvaluator | IllegalArgumentException | Fixed — `ExpressionEvaluationException` |
| §— SYSTEM_USER_ID constant | User | Magic string | Fixed — `User.SYSTEM_USER_ID`, `User.SYSTEM` |
| §3 UserResolver interface (B3) | UserResolver / InMemoryUserResolver | Missing | Fixed — `resolve`, `resolveAll`, `resolveByRole`; injected into `DefaultStepActivator` and `ProcessEngine` |
| §15 PENDING step lifecycle (B2) | StepState / ProcessEngine | Steps created IN_PROGRESS, no `openStep` | Fixed — `createdAt`/`openedAt` on `StepState`; `openStep` on `ProcessEngine`; escalation moves to `openStep` |
| §3 ActionType.OPEN (B2) | ActionType / Action | Missing | Fixed — `ActionType.OPEN`; `Action.open()` factory; `resolvedStatus()` maps to `IN_PROGRESS` |
| §12 TransitionGuard.assertStepIsInProgress (B2) | TransitionGuard | Missing | Fixed — guards `advanceStep`; redundant inline check removed from `assertActionIsValid` |
| §6 FieldSchema compiledPattern (B1) | FieldSchema | Missing | Fixed — 4th record component; compact constructor compiles eagerly; `equals/hashCode` exclude `Pattern` reference |
| §9 DefinitionValidator.compilePatterns (B1) | DefinitionValidator | Missing | Fixed — validates regex syntax at `loadDefinition` time; wraps `PatternSyntaxException` as `DefinitionValidationException` |
| §13 DefaultFieldValidator regex (B1) | DefaultFieldValidator | `Pattern.compile()` on every call | Fixed — uses `schema.compiledPattern()` directly; import removed |

---