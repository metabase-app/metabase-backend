-- Historical identifiers deliberately survive deletion of users and catalogue entities.
CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    action VARCHAR(50) NOT NULL CHECK (action IN (
        'FAVOURITE_ADDED', 'FAVOURITE_REMOVED', 'RATING_CHANGED', 'IMPORT_COMPLETED', 'IMPORT_FAILED')),
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    actor_user_id BIGINT,
    system_name VARCHAR(100),
    audience VARCHAR(30) NOT NULL CHECK (audience IN ('USER_ACTIVITY', 'INTERNAL')),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    entity_id BIGINT,
    details JSONB NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    CHECK ((actor_type = 'USER' AND actor_user_id > 0 AND actor_user_id IS NOT NULL AND system_name IS NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL AND system_name IS NOT NULL AND btrim(system_name) <> '')),
    CHECK (entity_id IS NULL OR entity_id > 0)
);

CREATE TABLE event_deliveries (
    event_id UUID NOT NULL REFERENCES event_outbox(id) ON DELETE CASCADE,
    consumer VARCHAR(30) NOT NULL CHECK (consumer IN ('AUDIT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    PRIMARY KEY (event_id, consumer)
);
CREATE INDEX idx_event_deliveries_pending ON event_deliveries (consumer, created_at, event_id)
    WHERE processed_at IS NULL;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    action VARCHAR(50) NOT NULL CHECK (action IN (
        'FAVOURITE_ADDED', 'FAVOURITE_REMOVED', 'RATING_CHANGED', 'IMPORT_COMPLETED', 'IMPORT_FAILED')),
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    actor_user_id BIGINT,
    system_name VARCHAR(100),
    audience VARCHAR(30) NOT NULL CHECK (audience IN ('USER_ACTIVITY', 'INTERNAL')),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    entity_id BIGINT,
    details JSONB NOT NULL CHECK (jsonb_typeof(details) = 'object'),
    CHECK ((actor_type = 'USER' AND actor_user_id > 0 AND actor_user_id IS NOT NULL AND system_name IS NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL AND system_name IS NOT NULL AND btrim(system_name) <> '')),
    CHECK (entity_id IS NULL OR entity_id > 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_events_operation ON audit_events (operation_id);
CREATE INDEX idx_audit_events_user_activity ON audit_events (actor_user_id, occurred_at DESC, id)
    WHERE audience = 'USER_ACTIVITY';
CREATE INDEX idx_audit_events_entity ON audit_events (entity_id, occurred_at DESC);
CREATE INDEX idx_audit_events_internal ON audit_events (occurred_at DESC, id)
    WHERE audience = 'INTERNAL';
