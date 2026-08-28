package me.lewisblackburn.metabase.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    private static final Movie MOVIE = new Movie(
            1L, "The Matrix", "A computer hacker discovers the truth.", LocalDate.of(1999, 3, 31), 136);

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieController controller;

    @Test
    void returnsMoviesFromRepository() {
        given(movieRepository.findAll()).willReturn(List.of(MOVIE));

        List<Movie> result = controller.movies();

        assertThat(result).containsExactly(MOVIE);
    }

    @Test
    void returnsMovieById() {
        given(movieRepository.find(MOVIE.id())).willReturn(MOVIE);

        Movie result = controller.movie(MOVIE.id());

        assertThat(result).isSameAs(MOVIE);
    }

    @Test
    void mapsMovieCast() {
        CastMember keanu = new CastMember(2L, "Keanu Reeves", "Neo", 0);
        given(movieRepository.findCastByMovieIds(List.of(MOVIE.id())))
                .willReturn(Map.of(MOVIE.id(), List.of(keanu)));

        Map<Movie, List<CastMember>> result = controller.cast(List.of(MOVIE));

        assertThat(result).containsEntry(MOVIE, List.of(keanu));
    }

    @Test
    void mapsMissingCastToEmptyList() {
        given(movieRepository.findCastByMovieIds(List.of(MOVIE.id()))).willReturn(Map.of());

        Map<Movie, List<CastMember>> result = controller.cast(List.of(MOVIE));

        assertThat(result).containsEntry(MOVIE, List.of());
    }
}

