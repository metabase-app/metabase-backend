package me.lewisblackburn.metabase.event;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserAuditEvent(
        UUID id,
        UUID operationId,
        OffsetDateTime occurredAt,
        String action,
        Long actorUserId,
        String outcome,
        Long entityId,
        String details
) {
}
