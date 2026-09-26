package me.lewisblackburn.metabase.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.movie.model.CastMember;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    private static final Movie MOVIE = new Movie(1L, "The Matrix",
            "A computer hacker discovers the truth.", LocalDate.of(1999, 3, 31), 136);

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieController controller;

    @Test
    void returnsMoviesFromRepository() {
        // Given the repository returns a movie.
        given(movieRepository.findAll()).willReturn(List.of(MOVIE));

        // When all movies are requested from the controller.
        List<Movie> result = controller.movies();

        // Then the controller returns the repository result.
        assertThat(result).containsExactly(MOVIE);
    }

    @Test
    void returnsMovieById() {
        // Given the repository contains a movie with the requested ID.
        given(movieRepository.find(MOVIE.id())).willReturn(MOVIE);

        // When that movie is requested from the controller.
        Movie result = controller.movie(MOVIE.id());

        // Then the controller returns the matching movie.
        assertThat(result).isSameAs(MOVIE);
    }

    @Test
    void mapsMovieCast() {
        // Given the repository returns a cast member for the movie.
        CastMember keanu = new CastMember(2L, "Keanu Reeves", "Neo", 0);
        given(movieRepository.findCastByMovieIds(List.of(MOVIE.id())))
                .willReturn(Map.of(MOVIE.id(), List.of(keanu)));

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
}
