package me.lewisblackburn.metabase.movie;

import static me.lewisblackburn.metabase.jooq.tables.Credits.CREDITS;
import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.Movies.MOVIES;
import static me.lewisblackburn.metabase.jooq.tables.People.PEOPLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.credit.CreditDepartment;
import me.lewisblackburn.metabase.credit.CreditJob;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.CrewMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
@Sql("/sql/cleanup.sql")
class MovieRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
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
        // Given a movie and an acting credit have been stored.
        Long movieId = insertEntity("MOVIE", "The Matrix");
        dsl.insertInto(MOVIES)
            .set(MOVIES.ENTITY_ID, movieId)
            .set(MOVIES.RELEASE_DATE, LocalDate.of(1999, 3, 31))
            .set(MOVIES.RUNTIME_MINUTES, 136)
            .execute();
        Long alienId = insertEntity("MOVIE", "Alien");
        dsl.insertInto(MOVIES).set(MOVIES.ENTITY_ID, alienId).execute();

        Long personId = insertEntity("PERSON", "Keanu Reeves");
        dsl.insertInto(PEOPLE).set(PEOPLE.ENTITY_ID, personId).execute();
        dsl.insertInto(CREDITS)
            .set(CREDITS.CREDITED_ENTITY_ID, movieId)
            .set(CREDITS.PERSON_ID, personId)
            .set(CREDITS.DEPARTMENT, CreditDepartment.ACTING.getDatabaseValue())
            .set(CREDITS.JOB, CreditJob.ACTOR.getDatabaseValue())
            .set(CREDITS.CHARACTER_NAME, "Neo")
            .set(CREDITS.CREDIT_ORDER, 0)
            .execute();

        // When movie results are read in two pages and a movie is loaded by ID.
        List<Movie> firstPage = movieRepository.findAll(0, 1);
        List<Movie> secondPage = movieRepository.findAll(1, 1);
        Movie movie = movieRepository.find(movieId);
        Map<Long, List<CastMember>> cast = movieRepository.findCastByMovieIds(List.of(movieId));

        // Then pages are stable by title, and the movie has its credited cast member.
        assertThat(firstPage).extracting(Movie::title).containsExactly("Alien");
        assertThat(secondPage).extracting(Movie::title).containsExactly("The Matrix");
        assertThat(movie.releaseDate()).isEqualTo(LocalDate.of(1999, 3, 31));
        assertThat(cast.get(movieId))
            .extracting(CastMember::name, CastMember::character)
            .containsExactly(assertThatTuple("Keanu Reeves", "Neo"));
    }

    @Test
    void findsMoviesAndTheirCrew() {
        // Given a movie and a directing credit have been stored.
        Long movieId = insertEntity("MOVIE", "The Matrix");
        dsl.insertInto(MOVIES).set(MOVIES.ENTITY_ID, movieId).execute();
        Long personId = insertEntity("PERSON", "Lana Wachowski");
        dsl.insertInto(PEOPLE).set(PEOPLE.ENTITY_ID, personId).execute();
        dsl.insertInto(CREDITS)
            .set(CREDITS.CREDITED_ENTITY_ID, movieId)
            .set(CREDITS.PERSON_ID, personId)
            .set(CREDITS.DEPARTMENT, "Directing")
            .set(CREDITS.JOB, "Director")
            .execute();

        // When the movie's crew is loaded.
        Map<Long, List<CrewMember>> crew = movieRepository.findCrewByMovieIds(List.of(movieId));

        // Then the directing credit is returned with its department and job.
        assertThat(crew.get(movieId))
            .extracting(CrewMember::name, CrewMember::department, CrewMember::job)
            .containsExactly(assertThatTuple("Lana Wachowski", "Directing", "Director"));
    }

    private Long insertEntity(String type, String name) {
        return dsl
            .insertInto(ENTITIES)
            .set(ENTITIES.ENTITY_TYPE, type)
            .set(ENTITIES.DISPLAY_NAME, name)
            .returning(ENTITIES.ID)
            .fetchOne(ENTITIES.ID);
    }

    private org.assertj.core.groups.Tuple assertThatTuple(String first, String second) {
        return org.assertj.core.groups.Tuple.tuple(first, second);
    }

    private org.assertj.core.groups.Tuple assertThatTuple(
        String first,
        String second,
        String third
    ) {
        return org.assertj.core.groups.Tuple.tuple(first, second, third);
    }
}
