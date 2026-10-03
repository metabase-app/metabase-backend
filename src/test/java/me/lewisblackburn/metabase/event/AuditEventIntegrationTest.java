package me.lewisblackburn.metabase.event;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
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
        return ActionEvent.builder()
                .id(UUID.randomUUID())
                .operationId(UUID.randomUUID())
                .occurredAt(OffsetDateTime.now())
                .action(EventAction.RATING_CHANGED)
                .actorType(EventActorType.USER)
                .actorUserId(42L)
                .systemName(null)
                .audience(EventAudience.USER_ACTIVITY)
                .outcome(EventOutcome.SUCCESS)
                .entityId(100L)
                .details(JSONB.valueOf("{\"previous\":7,\"new\":9}"))
                .build();
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

    @Autowired
    private UserAuditRepository userAuditRepository;

    @Test
    void pagesOnlyTheActorsUserActivityInStableNewestFirstOrder() {
        // Given two events with the same time, an older event, and unrelated audit rows.
        OffsetDateTime recent = OffsetDateTime.parse("2026-01-02T12:00:00Z");
        OffsetDateTime older = recent.minusDays(1);
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        insertUserLog(firstId, recent, 42L, "USER_ACTIVITY");
        insertUserLog(secondId, recent, 42L, "USER_ACTIVITY");
        insertUserLog(olderId, older, 42L, "USER_ACTIVITY");
        insertUserLog(UUID.randomUUID(), recent, 42L, "INTERNAL");
        insertUserLog(UUID.randomUUID(), recent, 99L, "USER_ACTIVITY");

        // When the user's logs are read at increasing offsets.
        var first = userAuditRepository.findByActor(42L, 0, 1);
        var second = userAuditRepository.findByActor(42L, 1, 1);
        var third = userAuditRepository.findByActor(42L, 2, 1);

        // Then each event appears once, in index order, without other audiences or actors.
        assertThat(first).extracting(UserAuditEvent::id).containsExactly(firstId);
        assertThat(second).extracting(UserAuditEvent::id).containsExactly(secondId);
        assertThat(third).extracting(UserAuditEvent::id).containsExactly(olderId);
        assertThat(userAuditRepository.findByActor(99L, 0, 20)).hasSize(1);
    }

    @Test
    void rejectsInvalidUserLogPagination() {
        // Given a negative offset or a limit outside the supported range.
        // When the repository reads logs, then it rejects invalid pagination arguments.
        for (int limit : List.of(-1, 0, 101)) {
            assertThatThrownBy(() -> userAuditRepository.findByActor(42L, 0, limit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> userAuditRepository.findByActor(42L, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertUserLog(UUID id, OffsetDateTime at, Long actorId, String audience) {
        dsl.insertInto(AUDIT_EVENTS)
                .set(AUDIT_EVENTS.ID, id)
                .set(AUDIT_EVENTS.OPERATION_ID, id)
                .set(AUDIT_EVENTS.OCCURRED_AT, at)
                .set(AUDIT_EVENTS.ACTION, "USER_FOLLOWED")
                .set(AUDIT_EVENTS.ACTOR_TYPE, "USER")
                .set(AUDIT_EVENTS.ACTOR_USER_ID, actorId)
                .set(AUDIT_EVENTS.AUDIENCE, audience)
                .set(AUDIT_EVENTS.OUTCOME, "SUCCESS")
                .set(AUDIT_EVENTS.DETAILS, JSONB.valueOf("{}"))
                .execute();
    }

}
