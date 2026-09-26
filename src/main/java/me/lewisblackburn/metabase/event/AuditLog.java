package me.lewisblackburn.metabase.event;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLog {

    private final DSLContext dsl;

    // Callers must commit the event together with the successful domain change.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ActionEvent event) {
        dsl.insertInto(AUDIT_EVENTS).set(AUDIT_EVENTS.ID, event.id())
                .set(AUDIT_EVENTS.OPERATION_ID, event.operationId())
                .set(AUDIT_EVENTS.OCCURRED_AT, event.occurredAt())
                .set(AUDIT_EVENTS.ACTION, event.action().name())
                .set(AUDIT_EVENTS.ACTOR_TYPE, event.actorType().name())
                .set(AUDIT_EVENTS.ACTOR_USER_ID, event.actorUserId())
                .set(AUDIT_EVENTS.SYSTEM_NAME, event.systemName())
                .set(AUDIT_EVENTS.AUDIENCE, event.audience().name())
                .set(AUDIT_EVENTS.OUTCOME, event.outcome().name())
                .set(AUDIT_EVENTS.ENTITY_ID, event.entityId())
                .set(AUDIT_EVENTS.DETAILS, event.details()).execute();
    }
}
