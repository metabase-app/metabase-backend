package me.lewisblackburn.metabase.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ImportEvents {

    private final AuditLog auditLog;
    private final JsonMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(String source, String providerType, String externalId, Long entityId) {
        record(source, providerType, externalId, entityId, EventAction.IMPORT_COMPLETED,
                EventOutcome.SUCCESS, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String source, String providerType, String externalId,
            RuntimeException failure) {
        // Arbitrary exception messages can contain credentials, URLs or raw provider payloads.
        record(source, providerType, externalId, null, EventAction.IMPORT_FAILED,
                EventOutcome.FAILURE, Map.of("errorType", failure.getClass().getSimpleName()));
    }

    private void record(String source, String providerType, String externalId, Long entityId,
            EventAction action, EventOutcome outcome, Map<String, String> error) {
        UUID id = UUID.randomUUID();
        var details = Map.of("source", source, "providerEntityType", providerType,
                "providerEntityId", externalId, "error", error);

        auditLog.record(new ActionEvent(id, id, OffsetDateTime.now(), action, EventActorType.SYSTEM,
                null, source, EventAudience.INTERNAL, outcome, entityId,
                JSONB.valueOf(mapper.writeValueAsString(details))));
    }
}
