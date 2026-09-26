package me.lewisblackburn.metabase.event;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Sql(statements = "TRUNCATE audit_events")
class AuditEventIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private PlatformTransactionManager transactions;

    private ActionEvent event() {
        return new ActionEvent(UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now(),
                EventAction.RATING_CHANGED, EventActorType.USER, 42L, null,
                EventAudience.USER_ACTIVITY, EventOutcome.SUCCESS, 100L,
                JSONB.valueOf("{\"previous\":7,\"new\":9}"));
    }

    @Test
    void recordsHistoryInTheCallingTransaction() {
        // Given a user action changes a rating from seven to nine.
        var event = event();

        // When its transaction records the action, the history is immediately available.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auditLog.record(event);
            assertThat(dsl.fetchCount(AUDIT_EVENTS)).isEqualTo(1);
        });

        // Then committed history preserves identity, actor, audience and both values.
        var saved = dsl.selectFrom(AUDIT_EVENTS).fetchSingle();
        assertThat(saved.getId()).isEqualTo(event.id());
        assertThat(saved.getOperationId()).isEqualTo(event.operationId());
        assertThat(saved.getActorUserId()).isEqualTo(42L);
        assertThat(saved.getAudience()).isEqualTo("USER_ACTIVITY");
        assertThat(saved.getDetails()).isEqualTo(event.details());
    }

    @Test
    void requiresTransactionForRecording() {
        // Given an action is recorded without its domain transaction.
        var event = event();

        // When recording is attempted, then it is rejected before storing any history.
        assertThatThrownBy(() -> auditLog.record(event))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(dsl.fetchCount(AUDIT_EVENTS)).isZero();
    }

    @Test
    void rollbackRemovesAuditHistory() {
        // Given a domain transaction records an action before its work fails.
        var event = event();

        // When that transaction is rolled back.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auditLog.record(event);
            status.setRollbackOnly();
        });

        // Then no success record survives for the rolled-back action.
        assertThat(dsl.fetchCount(AUDIT_EVENTS)).isZero();
    }

    @Test
    void migrationPreservesPendingAuditEventsBeforeRemovingQueue() throws Exception {
        // Given a database at V5 with a pending event and an already recorded copy of another
        // event.
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("audit_upgrade");
        flyway.target("5").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO audit_upgrade");
            statement.execute(
                    """
                            INSERT INTO event_outbox
                                (id, operation_id, occurred_at, action, actor_type, system_name, audience, outcome, details)
                            SELECT gen_random_uuid(), gen_random_uuid(), CURRENT_TIMESTAMP,
                                   'IMPORT_COMPLETED', 'SYSTEM', 'TMDB', 'INTERNAL', 'SUCCESS', '{}'
                            FROM generate_series(1, 2)
                            """);
            statement.execute(
                    "INSERT INTO event_deliveries (event_id, consumer) SELECT id, 'AUDIT' FROM event_outbox");
            statement.execute(
                    """
                            INSERT INTO audit_events
                                (id, operation_id, occurred_at, action, actor_type, system_name, audience, outcome, details)
                            SELECT id, operation_id, occurred_at, action, actor_type, system_name, audience, outcome, details
                            FROM event_outbox LIMIT 1
                            """);

            // When the direct-audit migration is applied.
            flyway.target("latest").load().migrate();

            // Then history is preserved without duplicates and both queue tables are gone.
            try (var result = statement.executeQuery("SELECT count(*) FROM audit_events")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery(
                    "SELECT to_regclass('audit_upgrade.event_outbox'), to_regclass('audit_upgrade.event_deliveries')")) {
                result.next();
                assertThat(result.getString(1)).isNull();
                assertThat(result.getString(2)).isNull();
            }
        }
    }

}
