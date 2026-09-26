package me.lewisblackburn.metabase.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.config.GraphQlScalarConfiguration;
import me.lewisblackburn.metabase.integrations.tmdb.TmdbMovieImportService;
import me.lewisblackburn.metabase.movie.model.Movie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

@GraphQlTest({MovieImportController.class, MovieController.class})
@Import(GraphQlScalarConfiguration.class)
class MovieImportGraphQlTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private TmdbMovieImportService importService;

    @MockitoBean
    private MovieRepository movieRepository;

    @Test
    void returnsImportedMovieWithCanonicalId() {
        // Given the TMDB importer saves a movie with a canonical ID distinct from its provider ID.
        given(importService.importMovie(603L)).willReturn(new Movie(42L, "The Matrix",
                "A computer hacker discovers the truth.", LocalDate.of(1999, 3, 31), 136));
        given(movieRepository.findCastByMovieIds(List.of(42L))).willReturn(Map.of());

        // When a movie is imported using its provider and external ID.
        var response = request("603").execute();

        // Then GraphQL returns the saved movie with its current cast.
        response.path("importMovie.id").entity(String.class).isEqualTo("42")
                .path("importMovie.title").entity(String.class).isEqualTo("The Matrix")
                .path("importMovie.releaseDate").entity(String.class).isEqualTo("1999-03-31")
                .path("importMovie.runtimeMinutes").entity(Integer.class).isEqualTo(136)
                .path("importMovie.cast").entityList(Object.class).hasSize(0);
        verify(importService).importMovie(603L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "tt0133093", "0", "-1", "1.5", "9223372036854775808"})
    void rejectsInvalidTmdbIdsBeforeImport(String externalId) {
        // Given the request contains an invalid TMDB movie ID.

        // When the mutation is executed.
        var response = request(externalId).execute();

        // Then a readable input error is returned without calling the importer.
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getMessage())
                    .isEqualTo("TMDB movie ID must be a positive integer");
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("BAD_REQUEST");
            assertThat(errors.getFirst().getPath()).isEqualTo("importMovie");
        });
        verifyNoInteractions(importService);
    }

    @Test
    void rejectsUnsupportedProvider() {
        // Given a provider that is not supported by the GraphQL schema.

        // When the import mutation is executed.
        var response = graphQlTester.documentName("importMovie").variable("provider", "IMDB")
                .variable("externalId", "tt0133093").execute();

        // Then schema validation rejects the request before importing anything.
        response.errors().satisfy(errors -> assertThat(errors).isNotEmpty());
        verifyNoInteractions(importService);
    }

    @Test
    void reportsMissingProviderMovie() {
        // Given TMDB reports that the movie does not exist.
        given(importService.importMovie(603L))
                .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When the import mutation is executed.
        var response = request("603").execute();

        // Then the caller receives a useful not-found error.
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getMessage())
                    .isEqualTo("The external service could not find the requested resource");
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("NOT_FOUND");
            assertThat(errors.getFirst().getPath()).isEqualTo("importMovie");
        });
    }

    @Test
    void hidesProviderConnectionDetailsOnFailure() {
        // Given a provider connection fails with internal connection details.
        given(importService.importMovie(603L))
                .willThrow(new ResourceAccessException("private connection details"));

        // When the import mutation is executed.
        var response = request("603").execute();

        // Then GraphQL returns a safe provider failure message.
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getMessage())
                    .isEqualTo("An external service request failed");
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
            assertThat(errors.getFirst().getPath()).isEqualTo("importMovie");
        });
    }

    private GraphQlTester.Request<?> request(String externalId) {
        return graphQlTester.documentName("importMovie").variable("provider", "TMDB")
                .variable("externalId", externalId);
    }
}
