package me.lewisblackburn.metabase.movie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
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
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(MovieController.class)
@Import(GraphQlScalarConfiguration.class)
class MovieGraphQlTest {

    @MockitoBean
    private GraphQlRateLimiter rateLimiter;

    @BeforeEach
    void provideHttpContext() {
        var http = new ServletRequestAttributes(
                new MockHttpServletRequest());
        graphQlTester =
                ((ExecutionGraphQlServiceTester) graphQlTester).mutate()
                        .configureExecutionInput((input, builder) -> builder
                                .graphQLContext(context -> context.put("http", http)).build())
                        .build();
    }

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private MovieRepository movieRepository;

    @Test
    void returnsMovieWithCast() {
        // Given the repository contains a movie and its cast.
        Movie movie = Movie.builder()
                .id(1L)
                .title("The Matrix")
                .overview("A computer hacker discovers the truth.")
                .releaseDate(LocalDate.of(1999, 3, 31))
                .runtimeMinutes(136)
                .build();
        CastMember castMember = CastMember.builder()
                .personId(2L)
                .name("Keanu Reeves")
                .character("Neo")
                .order(0)
                .build();
        given(movieRepository.find(1L)).willReturn(movie);
        given(movieRepository.findCastByMovieIds(List.of(1L)))
                .willReturn(Map.of(1L, List.of(castMember)));

        // When the movie GraphQL query is executed.
        GraphQlTester.Response response =
                graphQlTester.documentName("movie").variable("id", "1").execute();

        // Then the response contains the movie and its cast details.
        response.path("movie.title").entity(String.class).isEqualTo("The Matrix")
                .path("movie.releaseDate").entity(String.class).isEqualTo("1999-03-31")
                .path("movie.cast[0].name").entity(String.class).isEqualTo("Keanu Reeves")
                .path("movie.cast[0].character").entity(String.class).isEqualTo("Neo");
    }
}
