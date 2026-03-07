CREATE TABLE process_definitions (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    name                         VARCHAR(255)                NOT NULL,
    published_at                 TIMESTAMPTZ                 NOT NULL,
    published_by                 VARCHAR(128)                NOT NULL,
    definition_payload           JSONB                       NOT NULL DEFAULT '{}'::jsonb,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version)
);

CREATE TABLE process_definition_steps (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    step_id                      VARCHAR(128)                NOT NULL,
    step_order                   INTEGER                     NOT NULL,
    step_type                    VARCHAR(32)                 NOT NULL,
    assignee_rule_type           VARCHAR(64)                 NOT NULL,
    assignee_rule_user_ids       JSONB                       NOT NULL DEFAULT '[]'::jsonb,
    assignee_rule_field_name     VARCHAR(128),
    quorum_mode                  VARCHAR(32),
    escalation_threshold_seconds BIGINT,
    escalation_target_user_id    VARCHAR(128),
    escalation_reason            TEXT,
    scheduled_offset_seconds     BIGINT,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version, step_id),
    CONSTRAINT fk_definition_steps_definition
        FOREIGN KEY (definition_id, version)
        REFERENCES process_definitions (definition_id, version),
    CONSTRAINT chk_step_order_positive
        CHECK (step_order > 0),
    CONSTRAINT chk_step_type
        CHECK (step_type IN ('TASK', 'APPROVAL', 'PARALLEL_APPROVAL', 'CHECKLIST', 'SIGNATURE')),
    CONSTRAINT chk_assignee_rule_type
        CHECK (assignee_rule_type IN ('INITIATOR', 'USER_IDS', 'FIELD_VALUE_USER_ID')),
    CONSTRAINT chk_quorum_mode
        CHECK (quorum_mode IS NULL OR quorum_mode IN ('ALL', 'MAJORITY')),
    CONSTRAINT chk_escalation_threshold
        CHECK (escalation_threshold_seconds IS NULL OR escalation_threshold_seconds > 0),
    CONSTRAINT chk_scheduled_offset
        CHECK (scheduled_offset_seconds IS NULL OR scheduled_offset_seconds >= 0)
);

CREATE TABLE process_definition_field_schemas (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    step_id                      VARCHAR(128)                NOT NULL,
    field_name                   VARCHAR(128)                NOT NULL,
    is_required                  BOOLEAN                     NOT NULL DEFAULT FALSE,
    regex_pattern                TEXT,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version, step_id, field_name),
    CONSTRAINT fk_definition_field_schemas_step
        FOREIGN KEY (definition_id, version, step_id)
        REFERENCES process_definition_steps (definition_id, version, step_id)
);

CREATE TABLE process_definition_validation_rules (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    step_id                      VARCHAR(128)                NOT NULL,
    rule_order                   INTEGER                     NOT NULL,
    expression                   TEXT                        NOT NULL,
    message                      TEXT                        NOT NULL,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version, step_id, rule_order),
    CONSTRAINT fk_definition_validation_rules_step
        FOREIGN KEY (definition_id, version, step_id)
        REFERENCES process_definition_steps (definition_id, version, step_id),
    CONSTRAINT chk_validation_rule_order_positive
        CHECK (rule_order > 0)
);

CREATE TABLE process_definition_routing_rules (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    source_step_id               VARCHAR(128)                NOT NULL,
    rule_order                   INTEGER                     NOT NULL,
    condition_expression         TEXT,
    is_default                   BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version, source_step_id, rule_order),
    CONSTRAINT fk_definition_routing_rules_step
        FOREIGN KEY (definition_id, version, source_step_id)
        REFERENCES process_definition_steps (definition_id, version, step_id),
    CONSTRAINT chk_routing_rule_order_positive
        CHECK (rule_order > 0),
    CONSTRAINT chk_default_rule_condition
        CHECK (
            (is_default = TRUE AND condition_expression IS NULL)
            OR (is_default = FALSE)
        )
);

CREATE TABLE process_definition_routing_targets (
    definition_id                VARCHAR(128)                NOT NULL,
    version                      INTEGER                     NOT NULL,
    source_step_id               VARCHAR(128)                NOT NULL,
    rule_order                   INTEGER                     NOT NULL,
    target_order                 INTEGER                     NOT NULL,
    target_step_id               VARCHAR(128)                NOT NULL,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (definition_id, version, source_step_id, rule_order, target_order),
    CONSTRAINT fk_definition_routing_targets_rule
        FOREIGN KEY (definition_id, version, source_step_id, rule_order)
        REFERENCES process_definition_routing_rules (definition_id, version, source_step_id, rule_order),
    CONSTRAINT fk_definition_routing_targets_target_step
        FOREIGN KEY (definition_id, version, target_step_id)
        REFERENCES process_definition_steps (definition_id, version, step_id),
    CONSTRAINT chk_routing_target_order_positive
        CHECK (target_order > 0)
);

CREATE TABLE process_instances (
    instance_id                  UUID                        NOT NULL,
    definition_id                VARCHAR(128)                NOT NULL,
    definition_version           INTEGER                     NOT NULL,
    initiator_user_id            VARCHAR(128)                NOT NULL,
    initiator_display_name       VARCHAR(255)                NOT NULL,
    status                       VARCHAR(32)                 NOT NULL,
    lock_version                 BIGINT                      NOT NULL DEFAULT 1,
    field_values                 JSONB                       NOT NULL DEFAULT '{}'::jsonb,
    created_at                   TIMESTAMPTZ                 NOT NULL,
    completed_at                 TIMESTAMPTZ,
    PRIMARY KEY (instance_id),
    CONSTRAINT fk_process_instances_definition
        FOREIGN KEY (definition_id, definition_version)
        REFERENCES process_definitions (definition_id, version),
    CONSTRAINT chk_instance_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'SUSPENDED')),
    CONSTRAINT chk_lock_version_positive
        CHECK (lock_version > 0)
);

CREATE TABLE step_states (
    step_state_id                UUID                        NOT NULL,
    instance_id                  UUID                        NOT NULL,
    step_id                      VARCHAR(128)                NOT NULL,
    assigned_to_user_id          VARCHAR(128)                NOT NULL,
    status                       VARCHAR(32)                 NOT NULL,
    started_at                   TIMESTAMPTZ                 NOT NULL,
    completed_at                 TIMESTAMPTZ,
    participant_id               VARCHAR(128),
    sequence_number              BIGINT                      NOT NULL,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (step_state_id),
    CONSTRAINT fk_step_states_instance
        FOREIGN KEY (instance_id)
        REFERENCES process_instances (instance_id),
    CONSTRAINT chk_step_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'ESCALATED', 'SKIPPED')),
    CONSTRAINT chk_step_sequence_positive
        CHECK (sequence_number > 0)
);

CREATE TABLE audit_entries (
    sequence_number              BIGINT                      NOT NULL,
    instance_id                  UUID                        NOT NULL,
    step_state_id                UUID,
    actor_user_id                VARCHAR(128)                NOT NULL,
    occurred_at                  TIMESTAMPTZ                 NOT NULL,
    from_status                  VARCHAR(32),
    to_status                    VARCHAR(32),
    action_type                  VARCHAR(32)                 NOT NULL,
    reason                       TEXT,
    metadata                     JSONB                       NOT NULL DEFAULT '{}'::jsonb,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sequence_number),
    CONSTRAINT fk_audit_entries_instance
        FOREIGN KEY (instance_id)
        REFERENCES process_instances (instance_id),
    CONSTRAINT chk_audit_action_type
        CHECK (action_type IN ('SUBMIT', 'APPROVE', 'REJECT', 'ESCALATE', 'REASSIGN', 'REOPEN')),
    CONSTRAINT chk_audit_from_status
        CHECK (
            from_status IS NULL
            OR from_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'ESCALATED', 'SKIPPED')
        ),
    CONSTRAINT chk_audit_to_status
        CHECK (
            to_status IS NULL
            OR to_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'ESCALATED', 'SKIPPED')
        )
);

CREATE TABLE scheduled_escalations (
    step_state_id                UUID                        NOT NULL,
    instance_id                  UUID                        NOT NULL,
    expected_status              VARCHAR(32)                 NOT NULL,
    deliver_after                TIMESTAMPTZ                 NOT NULL,
    reason                       TEXT,
    cancelled_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (step_state_id),
    CONSTRAINT fk_scheduled_escalations_instance
        FOREIGN KEY (instance_id)
        REFERENCES process_instances (instance_id),
    CONSTRAINT chk_escalation_expected_status
        CHECK (expected_status IN ('PENDING', 'IN_PROGRESS'))
);

CREATE TABLE domain_events (
    event_id                     UUID                        NOT NULL,
    event_type                   VARCHAR(128)                NOT NULL,
    instance_id                  UUID,
    step_state_id                UUID,
    payload                      JSONB                       NOT NULL DEFAULT '{}'::jsonb,
    published_at                 TIMESTAMPTZ                 NOT NULL,
    created_at                   TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id)
);

CREATE OR REPLACE FUNCTION raise_immutable_table_violation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only; % is not permitted', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_entries_prevent_update
BEFORE UPDATE ON audit_entries
FOR EACH ROW
EXECUTE FUNCTION raise_immutable_table_violation();

CREATE TRIGGER trg_audit_entries_prevent_delete
BEFORE DELETE ON audit_entries
FOR EACH ROW
EXECUTE FUNCTION raise_immutable_table_violation();

CREATE TRIGGER trg_process_definitions_prevent_update
BEFORE UPDATE ON process_definitions
FOR EACH ROW
EXECUTE FUNCTION raise_immutable_table_violation();

CREATE TRIGGER trg_process_definitions_prevent_delete
BEFORE DELETE ON process_definitions
FOR EACH ROW
EXECUTE FUNCTION raise_immutable_table_violation();
