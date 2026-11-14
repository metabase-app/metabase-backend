package me.lewisblackburn.metabase.event;

import static me.lewisblackburn.metabase.jooq.tables.AuditLog.AUDIT_LOG;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.pagination.Pagination;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAuditRepository {

    private final DSLContext dsl;

    public List<UserAuditEvent> findByActor(Long userId, int offset, int limit) {
        Pagination.validate(offset, limit);
        return dsl
            .select(
                AUDIT_LOG.ID,
                AUDIT_LOG.OPERATION_ID,
                AUDIT_LOG.OCCURRED_AT,
                AUDIT_LOG.ACTOR_USER_ID,
                AUDIT_LOG.TABLE_NAME,
                AUDIT_LOG.OPERATION,
                AUDIT_LOG.ROW_KEY,
                AUDIT_LOG.BEFORE_DATA,
                AUDIT_LOG.AFTER_DATA
            )
            .from(AUDIT_LOG)
            .where(AUDIT_LOG.ACTOR_USER_ID.eq(userId))
            .orderBy(AUDIT_LOG.OCCURRED_AT.desc(), AUDIT_LOG.ID.asc())
            .offset(offset)
            .limit(limit)
            .fetch(row ->
                UserAuditEvent.builder()
                    .id(row.get(AUDIT_LOG.OPERATION_ID))
                    .operationId(row.get(AUDIT_LOG.OPERATION_ID))
                    .occurredAt(row.get(AUDIT_LOG.OCCURRED_AT))
                    .actorUserId(row.get(AUDIT_LOG.ACTOR_USER_ID))
                    .tableName(row.get(AUDIT_LOG.TABLE_NAME))
                    .operation(row.get(AUDIT_LOG.OPERATION))
                    .rowKey(data(row.get(AUDIT_LOG.ROW_KEY)))
                    .beforeData(data(row.get(AUDIT_LOG.BEFORE_DATA)))
                    .afterData(data(row.get(AUDIT_LOG.AFTER_DATA)))
                    .build()
            );
    }

    private static String data(JSONB value) {
        return value == null ? null : value.data();
    }
}
