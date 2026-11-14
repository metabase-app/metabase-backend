CREATE TABLE audit_changes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id BIGINT,
    system_name VARCHAR(100),
    table_name VARCHAR(100) NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_key JSONB NOT NULL CHECK (jsonb_typeof(row_key) = 'object'),
    changes JSONB NOT NULL CHECK (jsonb_typeof(changes) = 'object')
);

CREATE INDEX idx_audit_changes_operation ON audit_changes (operation_id, id);

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
    changes JSONB;
    key_column TEXT;
BEGIN
    operation_id := NULLIF(current_setting('app.audit_operation_id', true), '')::UUID;
    IF operation_id IS NULL THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    actor_user_id := NULLIF(current_setting('app.audit_actor_user_id', true), '')::BIGINT;
    system_name := NULLIF(current_setting('app.audit_system_name', true), '');
    old_row := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) ELSE '{}'::jsonb END;
    new_row := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) ELSE '{}'::jsonb END;

    FOREACH key_column IN ARRAY TG_ARGV LOOP
        row_key := row_key || jsonb_build_object(
            key_column,
            COALESCE(new_row -> key_column, old_row -> key_column)
        );
    END LOOP;

    IF TG_OP = 'INSERT' THEN
        changes := jsonb_build_object('after', new_row);
    ELSIF TG_OP = 'DELETE' THEN
        changes := jsonb_build_object('before', old_row);
    ELSE
        SELECT COALESCE(
            jsonb_object_agg(
                key_name,
                jsonb_build_object('before', old_row -> key_name, 'after', new_row -> key_name)
            ),
            '{}'::jsonb
        )
        INTO changes
        FROM jsonb_object_keys(old_row || new_row) AS keys(key_name)
        WHERE old_row -> key_name IS DISTINCT FROM new_row -> key_name;

        IF changes = '{}'::jsonb THEN
            RETURN NEW;
        END IF;
    END IF;

    INSERT INTO audit_changes (
        operation_id, actor_user_id, system_name, table_name, operation, row_key, changes
    ) VALUES (
        operation_id,
        actor_user_id,
        system_name,
        TG_TABLE_NAME,
        TG_OP,
        row_key,
        changes
    );

    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER audit_changes_entities
AFTER INSERT OR UPDATE OR DELETE ON entities
FOR EACH ROW EXECUTE FUNCTION record_audit_change('id');

CREATE TRIGGER audit_changes_movies
AFTER INSERT OR UPDATE OR DELETE ON movies
FOR EACH ROW EXECUTE FUNCTION record_audit_change('entity_id');

CREATE TRIGGER audit_changes_users
AFTER INSERT OR UPDATE OR DELETE ON users
FOR EACH ROW EXECUTE FUNCTION record_audit_change('id');

CREATE TRIGGER audit_changes_user_favourites
AFTER INSERT OR UPDATE OR DELETE ON user_favourites
FOR EACH ROW EXECUTE FUNCTION record_audit_change('user_id', 'entity_id');

CREATE TRIGGER audit_changes_user_ratings
AFTER INSERT OR UPDATE OR DELETE ON user_ratings
FOR EACH ROW EXECUTE FUNCTION record_audit_change('user_id', 'entity_id');

CREATE TRIGGER audit_changes_user_follows
AFTER INSERT OR UPDATE OR DELETE ON user_follows
FOR EACH ROW EXECUTE FUNCTION record_audit_change('follower_id', 'followed_id');

CREATE TRIGGER audit_changes_user_lists
AFTER INSERT OR UPDATE OR DELETE ON user_lists
FOR EACH ROW EXECUTE FUNCTION record_audit_change('id');

CREATE TRIGGER audit_changes_user_list_items
AFTER INSERT OR UPDATE OR DELETE ON user_list_items
FOR EACH ROW EXECUTE FUNCTION record_audit_change('list_id', 'entity_id');

CREATE TRIGGER audit_changes_provider_entity_mappings
AFTER INSERT OR UPDATE OR DELETE ON provider_entity_mappings
FOR EACH ROW EXECUTE FUNCTION record_audit_change('id');
