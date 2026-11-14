package me.lewisblackburn.metabase.event;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditContext {

    private final DSLContext dsl;

    public void setUserAuditContext(Long userId) {
        UUID operationId = UUID.randomUUID();
        set("app.audit_operation_id", operationId.toString());
        set("app.audit_actor_user_id", userId.toString());
        set("app.audit_system_name", null);
    }

    public void setSystemAuditContext(String systemName) {
        UUID operationId = UUID.randomUUID();
        set("app.audit_operation_id", operationId.toString());
        set("app.audit_actor_user_id", null);
        set("app.audit_system_name", systemName);
    }

    private void set(String name, String value) {
        dsl.query("select set_config(?, ?, true)", name, value == null ? "" : value).execute();
    }
}
