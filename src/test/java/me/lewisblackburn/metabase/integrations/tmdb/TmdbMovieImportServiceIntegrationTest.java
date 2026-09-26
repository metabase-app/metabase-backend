package me.lewisblackburn.metabase.integrations.tmdb;

import static me.lewisblackburn.metabase.jooq.tables.DataSources.DATA_SOURCES;
import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.Movies.MOVIES;
import static me.lewisblackburn.metabase.jooq.tables.ProviderEntityMappings.PROVIDER_ENTITY_MAPPINGS;
import static me.lewisblackburn.metabase.support.ClasspathFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.JooqMovieImportRepository;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Testcontainers
@Sql(statements = "TRUNCATE TABLE entities, data_sources, audit_events RESTART IDENTITY CASCADE")
class TmdbMovieImportServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TmdbMovieImportService service;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private TmdbMovieClient client;

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @Autowired
    private JooqMovieImportRepository importRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void recordsProviderFailuresAsInternalEvents() {
        // Given fetching a provider movie fails with a message that must not enter audit details.
        var failure = new ResourceAccessException("secret-token");
        given(client.getMovie(603L)).willThrow(failure);

        // When importing fails, its original exception is preserved and its audit event is saved.
        assertThatThrownBy(() -> service.importMovie(603L)).isSameAs(failure);

        // Then the failure is internal, identifies the provider movie, and excludes sensitive text.
        var event = dsl.fetchOne("SELECT * FROM audit_events");
        assertThat(event.get("action", String.class)).isEqualTo("IMPORT_FAILED");
        assertThat(event.get("audience", String.class)).isEqualTo("INTERNAL");
        assertThat(event.get("outcome", String.class)).isEqualTo("FAILURE");
        assertThat(event.get("details").toString()).contains("603", "ResourceAccessException")
                .doesNotContain("secret-token");
        assertThat(dsl.fetchCount(ENTITIES)).isZero();
    }

    @Test
    void importsMovieAndUpdatesItWithoutDuplicates() throws IOException {
        // Given TMDB supplies a complete movie, with fetching outside a database transaction.
        TmdbMovieDto source = jsonMapper.readValue(read("tmdb/movie-603.json"), TmdbMovieDto.class);
        given(client.getMovie(603L)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return source;
        });

        // When the movie is imported for the first time.
        Movie first = service.importMovie(603L);

        // Then the canonical movie and provider identity are stored separately.
        assertThat(first.id()).isNotNull().isNotEqualTo(603L);
        var event = dsl.fetchOne("SELECT * FROM audit_events");
        assertThat(event.get("entity_id", Long.class)).isEqualTo(first.id());
        assertThat(event.get("action", String.class)).isEqualTo("IMPORT_COMPLETED");
        assertThat(event.get("audience", String.class)).isEqualTo("INTERNAL");
        assertThat(first.title()).isEqualTo("The Matrix");
        assertThat(first.releaseDate()).isEqualTo(LocalDate.of(1999, 3, 30));
        assertThat(first.runtimeMinutes()).isEqualTo(136);
        assertThat(dsl.select(ENTITIES.ORIGINAL_LANGUAGE_CODE).from(ENTITIES)
                .where(ENTITIES.ID.eq(first.id())).fetchSingle(ENTITIES.ORIGINAL_LANGUAGE_CODE))
                .isEqualTo("en");
        assertThat(dsl.select(MOVIES.ORIGINAL_TITLE).from(MOVIES)
                .where(MOVIES.ENTITY_ID.eq(first.id())).fetchSingle(MOVIES.ORIGINAL_TITLE))
                .isEqualTo("The Matrix");
        var mapping = dsl.selectFrom(PROVIDER_ENTITY_MAPPINGS).fetchSingle();
        assertThat(mapping.getEntityId()).isEqualTo(first.id());
        assertThat(mapping.getProviderEntityId()).isEqualTo("603");
        assertThat(mapping.getProviderEntityType()).isEqualTo("MOVIE");
        assertThat(mapping.getLastSeenAt()).isNotNull();
        assertThat(dsl.selectFrom(DATA_SOURCES).fetchSingle().getName()).isEqualTo("TMDB");

        // Given the next response contains revised details and an older last-seen timestamp exists.
        OffsetDateTime old = OffsetDateTime.now().minusDays(1);
        dsl.update(PROVIDER_ENTITY_MAPPINGS).set(PROVIDER_ENTITY_MAPPINGS.LAST_SEEN_AT, old)
                .execute();
        given(client.getMovie(603L))
                .willReturn(payload("Updated title", "Updated original title", 140));

        // When the same provider movie is imported again.
        Movie updated = service.importMovie(603L);

        // Then its identity is preserved, details are refreshed and no rows are duplicated.
        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.runtimeMinutes()).isEqualTo(140);
        assertThat(dsl.selectFrom(MOVIES).fetchSingle().getOriginalTitle())
                .isEqualTo("Updated original title");
        assertThat(dsl.selectFrom(PROVIDER_ENTITY_MAPPINGS).fetchSingle().getLastSeenAt())
                .isAfter(old);
        assertThat(dsl.fetchCount(ENTITIES)).isEqualTo(1);
        assertThat(dsl.fetchCount(MOVIES)).isEqualTo(1);
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isEqualTo(1);
        assertThat(dsl.fetchCount(DATA_SOURCES)).isEqualTo(1);
    }

    @Test
    void rollsBackMovieWhenSuccessEventCannotBeStored() {
        // Given valid movie data but an audit log that temporarily rejects successful import
        // events.
        given(client.getMovie(603L)).willReturn(payload("The Matrix", "The Matrix", 136));
        dsl.execute(
                "ALTER TABLE audit_events ADD CONSTRAINT test_reject_success CHECK (action <> 'IMPORT_COMPLETED')");
        try {
            // When saving the event fails after the catalogue writes.
            assertThatThrownBy(() -> service.importMovie(603L))
                    .isInstanceOf(RuntimeException.class);

            // Then the catalogue rolls back and only a separate failure event survives.
            assertThat(dsl.fetchCount(ENTITIES)).isZero();
            assertThat(dsl.fetchCount(MOVIES)).isZero();
            assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isZero();
            assertThat(dsl.fetchOne("SELECT action FROM audit_events").get(0, String.class))
                    .isEqualTo("IMPORT_FAILED");
        } finally {
            dsl.execute("ALTER TABLE audit_events DROP CONSTRAINT test_reject_success");
        }
    }

    @Test
    void rollsBackNewMovieWhenMovieDetailsCannotBeStored() {
        // Given TMDB supplies an original title that exceeds the database column limit.
        given(client.getMovie(603L)).willReturn(payload("The Matrix", "x".repeat(501), 136));

        // When the movie-specific write fails after creating the entity and mapping.
        assertThatThrownBy(() -> service.importMovie(603L)).isInstanceOf(RuntimeException.class);

        // Then the entire save is rolled back, including the source created during this attempt.
        assertThat(dsl.fetchCount(ENTITIES)).isZero();
        assertThat(dsl.fetchCount(MOVIES)).isZero();
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isZero();
        assertThat(dsl.fetchCount(DATA_SOURCES)).isZero();
        assertThat(dsl.fetchOne("SELECT action FROM audit_events").get(0, String.class))
                .isEqualTo("IMPORT_FAILED");
    }

    @Test
    void rollsBackChangesToExistingMovieWhenUpdateFails() {
        // Given an existing movie and a subsequent payload that cannot be stored.
        given(client.getMovie(603L)).willReturn(payload("The Matrix", "The Matrix", 136));
        Movie original = service.importMovie(603L);
        var mapping = dsl.selectFrom(PROVIDER_ENTITY_MAPPINGS).fetchSingle();
        given(client.getMovie(603L)).willReturn(payload("Changed title", "x".repeat(501), 140));

        // When updating the movie fails after changing its shared entity fields.
        assertThatThrownBy(() -> service.importMovie(603L)).isInstanceOf(RuntimeException.class);

        // Then the original entity, movie details and mapping timestamp remain intact.
        assertThat(dsl.selectFrom(ENTITIES).fetchSingle().getDisplayName())
                .isEqualTo(original.title());
        assertThat(dsl.selectFrom(MOVIES).fetchSingle().getRuntimeMinutes()).isEqualTo(136);
        assertThat(dsl.selectFrom(PROVIDER_ENTITY_MAPPINGS).fetchSingle().getLastSeenAt())
                .isEqualTo(mapping.getLastSeenAt());
    }

    @Test
    void concurrentImportsUseOneCanonicalMovie() throws Exception {
        // Given two callers receive the same provider movie at the same time.
        var bothFetched = new java.util.concurrent.CyclicBarrier(2);
        TmdbMovieDto source = payload("The Matrix", "The Matrix", 136);
        given(client.getMovie(603L)).willAnswer(invocation -> {
            bothFetched.await(10, TimeUnit.SECONDS);
            return source;
        });

        // When both callers import the movie concurrently.
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.importMovie(603L));
            var second = executor.submit(() -> service.importMovie(603L));

            // Then both return the same identity and only one movie and mapping exist.
            assertThat(first.get(20, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(20, TimeUnit.SECONDS).id());
        }
        assertThat(dsl.fetchCount(ENTITIES)).isEqualTo(1);
        assertThat(dsl.fetchCount(MOVIES)).isEqualTo(1);
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isEqualTo(1);
    }

    @Test
    void mutationPersistsMovieAndRepeatedImportReturnsSameId() throws IOException {
        // Given a real GraphQL service and database with a fixture-backed TMDB client.
        given(client.getMovie(603L))
                .willReturn(jsonMapper.readValue(read("tmdb/movie-603.json"), TmdbMovieDto.class));
        var tester = ExecutionGraphQlServiceTester.create(graphQlService);

        // When the movie is imported twice through the public mutation.
        String id = tester.documentName("importMovie").variable("provider", "TMDB")
                .variable("externalId", "603").execute().path("importMovie.id").entity(String.class)
                .get();
        tester.documentName("importMovie").variable("provider", "TMDB")
                .variable("externalId", "603").execute().path("importMovie.id").entity(String.class)
                .isEqualTo(id);

        // Then it can be queried by its canonical ID and only one saved movie exists.
        tester.documentName("movie").variable("id", id).execute().path("movie.title")
                .entity(String.class).isEqualTo("The Matrix").path("movie.runtimeMinutes")
                .entity(Integer.class).isEqualTo(136).path("movie.cast").entityList(Object.class)
                .hasSize(0);
        assertThat(dsl.fetchCount(MOVIES)).isEqualTo(1);
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isEqualTo(1);
    }

    @Test
    void keepsDifferentProviderNamespacesSeparate() {
        // Given identical external IDs and titles can occur in different provider namespaces.
        MovieImportData data = new MovieImportData("Shared title", null, null, null, null, null);

        // When movie imports come from different sources or provider entity types.
        Movie first = importRepository.save("SOURCE_A", "MOVIE", "123", data);
        Movie otherSource = importRepository.save("SOURCE_B", "MOVIE", "123", data);
        Movie otherType = importRepository.save("SOURCE_A", "FILM", "123", data);

        // Then mappings retain distinct canonical identities rather than matching on ID or title
        // alone.
        assertThat(java.util.List.of(first.id(), otherSource.id(), otherType.id()))
                .doesNotHaveDuplicates();
        assertThat(importRepository.save("SOURCE_A", "MOVIE", "123", data).id())
                .isEqualTo(first.id());
        assertThat(dsl.fetchCount(MOVIES)).isEqualTo(3);
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isEqualTo(3);
    }

    @Test
    void rejectsMappingToDifferentInternalEntityType() {
        // Given a provider mapping incorrectly points to an entity classified as a person.
        given(client.getMovie(603L)).willReturn(payload("The Matrix", "The Matrix", 136));
        Movie saved = service.importMovie(603L);
        dsl.update(ENTITIES).set(ENTITIES.ENTITY_TYPE, "PERSON").where(ENTITIES.ID.eq(saved.id()))
                .execute();
        given(client.getMovie(603L)).willReturn(payload("Changed title", "Changed title", 140));

        // When an import attempts to update the incorrectly mapped entity.
        assertThatThrownBy(() -> service.importMovie(603L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider mapping does not reference a movie");

        // Then the unrelated entity is not overwritten and no replacement movie is created.
        assertThat(dsl.selectFrom(ENTITIES).fetchSingle().getDisplayName()).isEqualTo("The Matrix");
        assertThat(dsl.selectFrom(MOVIES).fetchSingle().getRuntimeMinutes()).isEqualTo(136);
        assertThat(dsl.fetchCount(PROVIDER_ENTITY_MAPPINGS)).isEqualTo(1);
    }

    private TmdbMovieDto payload(String title, String originalTitle, int runtime) {
        var node = jsonMapper.createObjectNode().put("id", 603L).put("title", title)
                .put("original_title", originalTitle).put("runtime", runtime);
        return jsonMapper.treeToValue(node, TmdbMovieDto.class);
    }
}
