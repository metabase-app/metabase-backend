package me.lewisblackburn.metabase.integrations.tmdb;

import static me.lewisblackburn.metabase.support.ClasspathFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.time.LocalDate;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.JooqMovieImportRepository;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TmdbMovieImportServiceTest {

    @Mock
    private TmdbMovieClient client;

    @Mock
    private JooqMovieImportRepository repository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private TmdbMovieImportService service() {
        return new TmdbMovieImportService(client, new TmdbMovieMapper(), repository);
    }

    @Test
    void mapsAndSavesMovieWithProviderIdentity() throws IOException {
        // Given TMDB supplies a movie and the repository returns its saved canonical
        // representation.
        given(client.getMovie(603L))
                .willReturn(jsonMapper.readValue(read("tmdb/movie-603.json"), TmdbMovieDto.class));
        MovieImportData data =
                new MovieImportData("The Matrix", "A computer hacker discovers the truth.",
                        "The Matrix", "en", LocalDate.of(1999, 3, 30), 136);
        Movie saved = new Movie(42L, data.title(), data.overview(), data.releaseDate(),
                data.runtimeMinutes());
        given(repository.save("TMDB", "MOVIE", "603", data)).willReturn(saved);

        // When the provider movie is imported.
        Movie result = service().importMovie(603L);

        // Then normalized data and provider identity reach persistence and the saved movie is
        // returned.
        assertThat(result).isSameAs(saved);
        verify(repository).save("TMDB", "MOVIE", "603", data);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsBeforeFetching(Long id) {
        // Given an absent or nonpositive provider ID.

        // When importing is attempted, then validation fails before any external or database calls.
        assertThatIllegalArgumentException().isThrownBy(() -> service().importMovie(id))
                .withMessage("TMDB movie ID must be positive");
        verifyNoInteractions(client, repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{\"id\":604,\"title\":\"Wrong movie\"}",
            "{\"title\":\"Missing ID\"}"})
    void rejectsUnexpectedProviderResponses(String payload) {
        // Given TMDB returns no movie or a movie without the requested identity.
        given(client.getMovie(603L)).willReturn(jsonMapper.readValue(payload, TmdbMovieDto.class));

        // When importing is attempted, then the response is rejected before saving.
        assertThatIllegalStateException().isThrownBy(() -> service().importMovie(603L))
                .withMessage("TMDB returned an unexpected movie");
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotSaveWhenProviderFails() {
        // Given the provider request fails before movie data is available.
        var failure = new ResourceAccessException("Connection failed");
        given(client.getMovie(603L)).willThrow(failure);

        // When importing is attempted, then the failure propagates without database writes.
        assertThatThrownBy(() -> service().importMovie(603L)).isSameAs(failure);
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotSaveInvalidMovieData() {
        // Given the provider returns the expected identity but an invalid blank title.
        given(client.getMovie(603L)).willReturn(
                jsonMapper.readValue("{\"id\":603,\"title\":\" \"}", TmdbMovieDto.class));

        // When mapping rejects the payload, then no data is saved.
        assertThatIllegalArgumentException().isThrownBy(() -> service().importMovie(603L))
                .withMessage("Movie title must not be blank");
        verifyNoInteractions(repository);
    }
}
