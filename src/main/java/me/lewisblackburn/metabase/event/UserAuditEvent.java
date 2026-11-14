package me.lewisblackburn.metabase.event;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserAuditEvent(
    UUID id,
    UUID operationId,
    OffsetDateTime occurredAt,
    Long actorUserId,
    Long entityId,
    String tableName,
    String operation,
    String rowKey,
    String beforeData,
    String afterData
) {}
