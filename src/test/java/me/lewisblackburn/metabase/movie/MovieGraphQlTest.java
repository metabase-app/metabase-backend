package me.lewisblackburn.metabase.movie;

import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.config.GraphQlScalarConfiguration;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(MovieController.class)
@Import(GraphQlScalarConfiguration.class)
class MovieGraphQlTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private MovieRepository movieRepository;

    @Test
    void returnsMovieWithCast() {
        // Given the repository contains a movie and its cast.
        Movie movie = new Movie(
                1L, "The Matrix", "A computer hacker discovers the truth.", LocalDate.of(1999, 3, 31), 136);
        CastMember castMember = new CastMember(2L, "Keanu Reeves", "Neo", 0);
        given(movieRepository.find(1L)).willReturn(movie);
        given(movieRepository.findCastByMovieIds(List.of(1L))).willReturn(Map.of(1L, List.of(castMember)));

        // When the movie GraphQL query is executed.
        GraphQlTester.Response response = graphQlTester.documentName("movie")
                .variable("id", "1")
                .execute();

        // Then the response contains the movie and its cast details.
        response.path("movie.title").entity(String.class).isEqualTo("The Matrix")
                .path("movie.releaseDate").entity(String.class).isEqualTo("1999-03-31")
                .path("movie.cast[0].name").entity(String.class).isEqualTo("Keanu Reeves")
                .path("movie.cast[0].character").entity(String.class).isEqualTo("Neo");
    }
}
