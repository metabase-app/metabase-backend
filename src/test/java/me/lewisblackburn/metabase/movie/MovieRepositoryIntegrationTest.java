package me.lewisblackburn.metabase.movie;

import static me.lewisblackburn.metabase.jooq.tables.Credits.CREDITS;
import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.Movies.MOVIES;
import static me.lewisblackburn.metabase.jooq.tables.People.PEOPLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
@Sql("/sql/cleanup.sql")
class MovieRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void findsMoviesAndTheirCast() {
        Long movieId = insertEntity("MOVIE", "The Matrix");
        dsl.insertInto(MOVIES)
                .set(MOVIES.ENTITY_ID, movieId)
                .set(MOVIES.RELEASE_DATE, LocalDate.of(1999, 3, 31))
                .set(MOVIES.RUNTIME_MINUTES, 136)
                .execute();

        Long personId = insertEntity("PERSON", "Keanu Reeves");
        dsl.insertInto(PEOPLE).set(PEOPLE.ENTITY_ID, personId).execute();
        dsl.insertInto(CREDITS)
                .set(CREDITS.CREDITED_ENTITY_ID, movieId)
                .set(CREDITS.PERSON_ID, personId)
                .set(CREDITS.DEPARTMENT, "Acting")
                .set(CREDITS.JOB, "Actor")
                .set(CREDITS.CHARACTER_NAME, "Neo")
                .set(CREDITS.CREDIT_ORDER, 0)
                .execute();

        List<Movie> movies = movieRepository.findAll();
        Movie movie = movieRepository.find(movieId);
        Map<Long, List<CastMember>> cast = movieRepository.findCastByMovieIds(List.of(movieId));

        assertThat(movies).extracting(Movie::title).containsExactly("The Matrix");
        assertThat(movie.releaseDate()).isEqualTo(LocalDate.of(1999, 3, 31));
        assertThat(cast.get(movieId))
                .extracting(CastMember::name, CastMember::character)
                .containsExactly(assertThatTuple("Keanu Reeves", "Neo"));
    }

    private Long insertEntity(String type, String name) {
        return dsl.insertInto(ENTITIES)
                .set(ENTITIES.ENTITY_TYPE, type)
                .set(ENTITIES.DISPLAY_NAME, name)
                .returning(ENTITIES.ID)
                .fetchOne(ENTITIES.ID);
    }

    private org.assertj.core.groups.Tuple assertThatTuple(String first, String second) {
        return org.assertj.core.groups.Tuple.tuple(first, second);
    }
}
