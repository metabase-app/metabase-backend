package me.lewisblackburn.metabase.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.CrewMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    private static final Movie MOVIE = Movie.builder()
        .id(1L)
        .title("The Matrix")
        .overview("A computer hacker discovers the truth.")
        .releaseDate(LocalDate.of(1999, 3, 31))
        .runtimeMinutes(136)
        .build();

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GraphQlRateLimiter rateLimiter;

    private final ServletRequestAttributes http = new ServletRequestAttributes(
        new MockHttpServletRequest()
    );

    @InjectMocks
    private MovieController controller;

    @Test
    void returnsMoviesFromRepository() {
        // Given the repository returns a movie.
        given(movieRepository.findAll(5, 10)).willReturn(List.of(MOVIE));

        // When one page of movies is requested from the controller.
        List<Movie> result = controller.movies(5, 10, http);

        // Then the controller returns the repository result.
        assertThat(result).containsExactly(MOVIE);
    }

    @Test
    void returnsMovieById() {
        // Given the repository contains a movie with the requested ID.
        given(movieRepository.find(MOVIE.id())).willReturn(MOVIE);

        // When that movie is requested from the controller.
        Movie result = controller.movie(MOVIE.id(), http);

        // Then the controller returns the matching movie.
        assertThat(result).isSameAs(MOVIE);
    }

    @Test
    void mapsMovieCast() {
        // Given the repository returns a cast member for the movie.
        CastMember keanu = CastMember.builder()
            .personId(2L)
            .name("Keanu Reeves")
            .character("Neo")
            .order(0)
            .build();
        given(movieRepository.findCastByMovieIds(List.of(MOVIE.id()))).willReturn(
            Map.of(MOVIE.id(), List.of(keanu))
        );

        // When the controller resolves the movie's cast.
        Map<Movie, List<CastMember>> result = controller.cast(List.of(MOVIE));

        // Then the cast member is associated with that movie.
        assertThat(result).containsEntry(MOVIE, List.of(keanu));
    }

    @Test
    void mapsMissingCastToEmptyList() {
        // Given the repository returns no cast for the movie.
        given(movieRepository.findCastByMovieIds(List.of(MOVIE.id()))).willReturn(Map.of());

        // When the controller resolves the movie's cast.
        Map<Movie, List<CastMember>> result = controller.cast(List.of(MOVIE));

        // Then the movie is associated with an empty cast list.
        assertThat(result).containsEntry(MOVIE, List.of());
    }

    @Test
    void mapsMovieCrew() {
        // Given the repository returns a crew member for the movie.
        CrewMember director = CrewMember.builder()
            .personId(3L)
            .name("Lana Wachowski")
            .department("Directing")
            .job("Director")
            .build();
        given(movieRepository.findCrewByMovieIds(List.of(MOVIE.id()))).willReturn(
            Map.of(MOVIE.id(), List.of(director))
        );

        // When the controller resolves the movie's crew.
        Map<Movie, List<CrewMember>> result = controller.crew(List.of(MOVIE));

        // Then the crew member is associated with that movie.
        assertThat(result).containsEntry(MOVIE, List.of(director));
    }

    @Test
    void mapsMissingCrewToEmptyList() {
        // Given the repository returns no crew for the movie.
        given(movieRepository.findCrewByMovieIds(List.of(MOVIE.id()))).willReturn(Map.of());

        // When the controller resolves the movie's crew.
        Map<Movie, List<CrewMember>> result = controller.crew(List.of(MOVIE));

        // Then the movie is associated with an empty crew list.
        assertThat(result).containsEntry(MOVIE, List.of());
    }
}
