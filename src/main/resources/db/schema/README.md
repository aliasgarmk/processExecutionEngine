# Database Schema Notes

These SQL files define the initial relational schema for the process execution engine.

## Files

- `V1__process_engine_schema.sql`: core tables, constraints, and append-only protections
- `V2__process_engine_indexes.sql`: indexes for the primary runtime and audit access paths

## Assumptions

- Target database: PostgreSQL
- Timestamps are stored as `TIMESTAMPTZ`
- Flexible runtime and definition payloads use `JSONB`
- Audit and definition immutability are enforced with triggers

## Main design choices

- Process definitions are immutable and versioned by `(definition_id, version)`.
- Runtime state is split across `process_instances`, `step_states`, and `audit_entries`.
- Audit writes are append-only and ordered by `sequence_number`.
- Routing rules and validation rules are stored relationally for deterministic ordering and validation.
- Scheduled escalations and outbound domain events have their own tables so a queue-backed implementation can evolve without losing persistence visibility.
