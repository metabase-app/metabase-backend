-- Preserve pending history before retiring the background audit queue.
INSERT INTO audit_events (
    id, operation_id, occurred_at, action, actor_type, actor_user_id,
    system_name, audience, outcome, entity_id, details
)
SELECT id, operation_id, occurred_at, action, actor_type, actor_user_id,
       system_name, audience, outcome, entity_id, details
FROM event_outbox
ON CONFLICT (id) DO NOTHING;

DROP TABLE event_deliveries;
DROP TABLE event_outbox;
