package me.lewisblackburn.metabase.event;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.pagination.Pagination;
import org.jooq.DSLContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAuditRepository {
    private final DSLContext dsl;

    public List<UserAuditEvent> findByActor(Long userId, int offset, int limit) {
        Pagination.validate(offset, limit);
        return dsl
                .select(AUDIT_EVENTS.ID, AUDIT_EVENTS.OPERATION_ID,
                        AUDIT_EVENTS.OCCURRED_AT, AUDIT_EVENTS.ACTION,
                        AUDIT_EVENTS.ACTOR_USER_ID, AUDIT_EVENTS.OUTCOME,
                        AUDIT_EVENTS.ENTITY_ID, AUDIT_EVENTS.DETAILS)
                .from(AUDIT_EVENTS)
                .where(AUDIT_EVENTS.ACTOR_USER_ID.eq(userId))
                .and(AUDIT_EVENTS.AUDIENCE.eq(EventAudience.USER_ACTIVITY.name()))
                .orderBy(AUDIT_EVENTS.OCCURRED_AT.desc(), AUDIT_EVENTS.ID.asc())
                .offset(offset).limit(limit)
                .fetch(row -> UserAuditEvent.builder()
                        .id(row.get(AUDIT_EVENTS.ID))
                        .operationId(row.get(AUDIT_EVENTS.OPERATION_ID))
                        .occurredAt(row.get(AUDIT_EVENTS.OCCURRED_AT))
                        .action(row.get(AUDIT_EVENTS.ACTION))
                        .actorUserId(row.get(AUDIT_EVENTS.ACTOR_USER_ID))
                        .outcome(row.get(AUDIT_EVENTS.OUTCOME))
                        .entityId(row.get(AUDIT_EVENTS.ENTITY_ID))
                        .details(row.get(AUDIT_EVENTS.DETAILS).data())
                        .build());
    }
}
