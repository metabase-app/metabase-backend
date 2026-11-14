-- Keep one row per changed database row. The trigger owns the operation,
-- table name, row key, and before/after snapshots; application code only sets
-- the actor and operation context.
CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id UUID NOT NULL,
    actor_user_id BIGINT,
    system_name VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    table_name VARCHAR(100) NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_key JSONB NOT NULL CHECK (jsonb_typeof(row_key) = 'object'),
    before_data JSONB,
    after_data JSONB
);

CREATE INDEX idx_audit_log_actor_time
    ON audit_log (actor_user_id, occurred_at DESC, id);

CREATE INDEX idx_audit_log_table_time
    ON audit_log (table_name, occurred_at DESC, id);

CREATE INDEX idx_audit_log_operation
    ON audit_log (operation_id, id);

CREATE OR REPLACE FUNCTION record_audit_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_id UUID;
    actor_user_id BIGINT;
    system_name TEXT;
    old_row JSONB;
    new_row JSONB;
    row_key JSONB := '{}'::jsonb;
    key_column TEXT;
BEGIN
    operation_id := NULLIF(current_setting('app.audit_operation_id', true), '')::UUID;
    IF operation_id IS NULL THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    actor_user_id := NULLIF(current_setting('app.audit_actor_user_id', true), '')::BIGINT;
    system_name := NULLIF(current_setting('app.audit_system_name', true), '');
    old_row := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) END;
    new_row := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) END;

    FOREACH key_column IN ARRAY TG_ARGV LOOP
        row_key := row_key || jsonb_build_object(
            key_column,
            COALESCE(new_row -> key_column, old_row -> key_column)
        );
    END LOOP;

    IF TG_OP = 'UPDATE' AND old_row = new_row THEN
        RETURN NEW;
    END IF;

    INSERT INTO audit_log (
        operation_id,
        actor_user_id,
        system_name,
        table_name,
        operation,
        row_key,
        before_data,
        after_data
    ) VALUES (
        operation_id,
        actor_user_id,
        system_name,
        TG_TABLE_NAME,
        TG_OP,
        row_key,
        old_row,
        new_row
    );

    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TABLE audit_changes;
