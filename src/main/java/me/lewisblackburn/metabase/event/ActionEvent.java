package me.lewisblackburn.metabase.event;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.jooq.JSONB;
import org.springframework.util.Assert;

public record ActionEvent(
        UUID id,
        UUID operationId,
        OffsetDateTime occurredAt,
        EventAction action,
        EventActorType actorType,
        Long actorUserId,
        String systemName,
        EventAudience audience,
        EventOutcome outcome,
        Long entityId,
        JSONB details
) {

    public ActionEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(action);
        Objects.requireNonNull(actorType);
        Objects.requireNonNull(audience);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(details);

        Assert.isTrue(
                actorType == EventActorType.USER
                        ? actorUserId != null && actorUserId > 0 && systemName == null
                        : actorUserId == null && systemName != null && !systemName.isBlank(),
                "An event must identify either a user or a system actor");
        Assert.isTrue(entityId == null || entityId > 0, "Entity ID must be positive");
    }
}
