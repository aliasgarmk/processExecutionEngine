CREATE INDEX idx_process_definitions_latest
    ON process_definitions (definition_id, version DESC);

CREATE INDEX idx_process_definition_steps_order
    ON process_definition_steps (definition_id, version, step_order);

CREATE INDEX idx_process_definition_routing_rules_source
    ON process_definition_routing_rules (definition_id, version, source_step_id, rule_order);

CREATE INDEX idx_process_instances_definition_version
    ON process_instances (definition_id, definition_version);

CREATE INDEX idx_process_instances_status_created_at
    ON process_instances (status, created_at DESC);

CREATE INDEX idx_step_states_instance
    ON step_states (instance_id, sequence_number);

CREATE INDEX idx_step_states_instance_step
    ON step_states (instance_id, step_id, sequence_number);

CREATE INDEX idx_step_states_assigned_status
    ON step_states (assigned_to_user_id, status, started_at);

CREATE UNIQUE INDEX idx_step_states_active_participant
    ON step_states (instance_id, step_id, COALESCE(participant_id, assigned_to_user_id))
    WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE INDEX idx_audit_entries_instance_sequence
    ON audit_entries (instance_id, sequence_number);

CREATE INDEX idx_audit_entries_occurred_at
    ON audit_entries (occurred_at);

CREATE INDEX idx_scheduled_escalations_deliver_after
    ON scheduled_escalations (deliver_after)
    WHERE cancelled_at IS NULL;

CREATE INDEX idx_domain_events_type_published_at
    ON domain_events (event_type, published_at DESC);
