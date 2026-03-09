# System Evolution — Design Document

Three post-launch requirements and how the current architecture handles them.

---

## Scenario A — Process Versioning

### Current state

The engine already has the foundation for this. `DefinitionRegistry.resolveVersion(definitionId, version)` exists, and `ProcessInstance` stores `definitionVersion` alongside `definitionId`. Every `advanceStep` and `openStep` call resolves the definition by the instance's pinned version, not the latest. The in-memory implementation doesn't persist versions across restarts, but the contract is already correct.

### Data model changes

**Definition storage** currently keeps one copy per `definitionId`. To support multiple versions it becomes a compound key:

```
definition_versions
  definition_id    VARCHAR   -- logical ID ("capa")
  version          INT       -- monotonically increasing, assigned on register()
  name             VARCHAR
  payload          JSONB     -- full serialised ProcessDefinition
  published_at     TIMESTAMPTZ
  published_by     VARCHAR
  PRIMARY KEY (definition_id, version)
```

A `latest_version` view or a separate `definition_latest` lookup table (updated atomically on each publish) avoids a `MAX(version)` scan on every `startProcess` call.

**ProcessInstance** already carries `definitionVersion` — no schema change needed there. The foreign key is `(definition_id, definition_version) → definition_versions`.

**API surface** — `POST /api/definitions` assigns the next version and returns it. A new endpoint `GET /api/definitions/{id}/versions` lists all published versions. No breaking changes to existing callers.

### Migration risks

| Risk | Mitigation |
|---|---|
| Existing instances have `definitionVersion = 0` (the stub value used before persistence) | Back-fill: set `definition_version = 1` for all instances and ensure a version-1 row exists for every definition |
| Definition payload grows over time; JSONB column can become wide | Cap payload size at the API layer; extract step/routing tables if query patterns demand it |
| Callers that always want the "latest" behaviour may accidentally pin to an old version if they cache the definition object | Ensure `startProcess` always calls `resolveLatest` at the moment of instance creation, never at request-parse time |
| Rolling deploys: old service nodes may not understand new version columns | Add version column as nullable with a default of 1; make it NOT NULL only after all nodes are upgraded |

---

## Scenario B — Cross-Process Dependencies

### Modelling the dependency

Introduce a first-class `ProcessDependency` entity that is separate from both definitions and instances:

```
process_dependencies
  id                  UUID PK
  blocked_instance_id UUID  FK → process_instances
  blocked_step_id     VARCHAR            -- the step that cannot complete until unblocked
  blocking_instance_id UUID FK → process_instances
  blocking_step_id    VARCHAR            -- must reach this step (or beyond) to unblock
  status              ENUM(PENDING, SATISFIED, VOIDED)
  created_at          TIMESTAMPTZ
```

At `advanceStep` time, before routing to the next step, `TransitionGuard` (or a new `DependencyChecker` collaborator) queries for any `PENDING` dependency rows where `blocked_instance_id` = this instance and `blocked_step_id` = the step about to complete. If any exist, the transition is rejected with a new checked exception `BlockedByDependencyException`.

When the **blocking** instance reaches `blocking_step_id`, an event (`StepTransitioned`) triggers a `DependencyResolver` that:
1. Marks matching dependency rows as `SATISFIED`
2. Publishes a `DependencyUnblocked` event
3. The blocked instance's owner is notified (via `EventPublisher`) to retry the advance

This keeps dependency resolution asynchronous and avoids tight coupling between the two process engines.

### Preventing circular dependencies

Before persisting a new `ProcessDependency`, run a directed-graph reachability check:

```
canBlock(A, B):
  if A == B → reject (self-dependency)
  for each dependency where blocked = B:
    if blocking == A → reject (direct cycle)
    if canBlock(A, blocking) → reject (transitive cycle)
  → allow
```

In practice this is a BFS/DFS over the `process_dependencies` table scoped to `status = PENDING`. For typical process graphs (depth < 10, fan-out < 5) this is fast enough to run synchronously at dependency-creation time. For larger graphs, materialise a `dependency_graph` adjacency table and run the check there.

Enforce at the definition level too: if a definition template declares inter-process dependencies, validate the DAG at `loadDefinition` time so cycles are caught before any instances are created.

### If the blocking process is cancelled

Three viable policies — the right choice is customer-configurable per dependency:

| Policy | Behaviour | When to use |
|---|---|---|
| **Void and unblock** | Mark dependency `VOIDED`; the blocked step becomes advanceable | The blocked work is independent and should continue regardless |
| **Cascade cancel** | Cancel the blocked instance too; propagate recursively | The blocked work has no value without the blocking process completing |
| **Hold for review** | Leave the dependency `PENDING`; flag the blocked instance for manual resolution | Compliance scenarios where a human must decide |

Implement as a `CancellationPolicy` enum on the `ProcessDependency` row, evaluated by a `DependencyCancellationHandler` that subscribes to the `ProcessCancelled` event.

---

## Scenario C — Audit Trail at Scale

### Current storage shape

Each `AuditEntry` has: `audit_entry_id`, `instance_id`, `step_state_id`, `actor_id`, `from_status`, `to_status`, `action_type`, `recorded_at`, `sequence_number`.

At 500M rows the table is large but not unusual. The problem is query pattern mismatch: the existing index (`instance_id, recorded_at`) serves per-instance lookups well but forces a full table scan for compliance reports that filter only on `recorded_at`.

### Partitioning strategy

**Range-partition by month on `recorded_at`:**

```sql
CREATE TABLE audit_log (...)
PARTITION BY RANGE (recorded_at);

CREATE TABLE audit_log_2025_01 PARTITION OF audit_log
  FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
-- one partition per month, created automatically by a cron job
```

Each partition gets its own `(recorded_at, actor_id)` index for compliance scans and its own `(instance_id, sequence_number)` index for per-instance reads. Compliance queries that filter `recorded_at BETWEEN '2025-01-01' AND '2025-03-31'` hit at most three partitions instead of scanning 500M rows.

**Archival:** Partitions older than the compliance retention window (e.g. 7 years) are detached from the live table and moved to cold storage (S3 via `pg_partman` + `pg_cron`, or a purpose-built archival job). Detached partitions can still be queried by attaching them to a read replica on demand.

### Trade-offs

| Trade-off | Detail |
|---|---|
| Partition maintenance overhead | Monthly partitions must be pre-created; automate with `pg_partman` or a scheduled job |
| Cross-partition compliance queries still do parallel scans | Acceptable if the report is async (background job + download); not acceptable for synchronous API responses |
| Hot partition on current month | All writes land in one partition; mitigate with sub-partitioning by `hash(instance_id)` if write throughput demands it |
| Detached archive queries require manual attach | Wrap in a thin archive-query service that mounts partitions on demand |

### Would you change the storage layer?

For the **per-instance audit reads** (the hot path): no. A relational DB with the right indexes is the correct tool — ACID guarantees, straightforward joins, no operational complexity.

For **compliance reporting** (the cold path): yes, selectively. Push audit events to an **append-only columnar store** (Apache Iceberg on S3, or BigQuery/Redshift for SaaS) in parallel with the relational write:

```
advanceStep()
  → AuditWriter.record()          -- synchronous, relational DB (source of truth)
  → EventPublisher.publish()      -- async
       → AuditExportConsumer      -- streams to columnar store (e.g. Iceberg on S3)
```

The columnar store owns compliance reporting. It is eventually consistent (seconds to minutes behind) which is acceptable for scheduled compliance reports but not for real-time instance audit views. The relational table remains the authoritative source for the engine itself.

This dual-write approach avoids rewriting the engine's storage contract while solving the reporting problem at the layer where it actually lives: the analytics query engine.
