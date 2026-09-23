package me.lewisblackburn.metabase.integrations.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static me.lewisblackburn.metabase.support.ClasspathFixtures.read;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class TmdbMovieClientTest {
    private static final String BASE_URL = "https://tmdb.test/3";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String MOVIE_URL = BASE_URL + "/movie/603?append_to_response=credits,external_ids";

    private MockRestServiceServer server;
    private TmdbMovieClient movieClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        movieClient = new TmdbMovieClient(client);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void returnsDeserializedMovieDetails() throws IOException {
        // Given TMDB returns movie details with appended credits and external IDs.
        expectMovieRequest().andRespond(withSuccess(read("tmdb/movie-603.json"), MediaType.APPLICATION_JSON));

        // When the movie is requested by its TMDB ID.
        var movie = movieClient.getMovie(603L);

        // Then the movie and its nested resources are deserialized.
        assertThat(movie.id()).isEqualTo(603);
        assertThat(movie.title()).isEqualTo("The Matrix");
        assertThat(movie.releaseDate()).isEqualTo("1999-03-30");
        assertThat(movie.belongsToCollection().name()).isEqualTo("The Matrix Collection");
        assertThat(movie.genres()).singleElement().satisfies(genre -> assertThat(genre.name()).isEqualTo("Science Fiction"));
        assertThat(movie.productionCompanies()).singleElement().satisfies(company -> assertThat(company.originCountry()).isEqualTo("US"));
        assertThat(movie.productionCountries()).singleElement().satisfies(country -> assertThat(country.countryCode()).isEqualTo("US"));
        assertThat(movie.spokenLanguages()).singleElement().satisfies(language -> assertThat(language.languageCode()).isEqualTo("en"));
        assertThat(movie.credits().cast()).singleElement().satisfies(cast -> {
            assertThat(cast.name()).isEqualTo("Keanu Reeves");
            assertThat(cast.character()).isEqualTo("Neo");
        });
        assertThat(movie.credits().crew()).singleElement().satisfies(crew -> {
            assertThat(crew.department()).isEqualTo("Directing");
            assertThat(crew.job()).isEqualTo("Director");
        });
        assertThat(movie.externalIds().imdbId()).isEqualTo("tt0133093");
    }

    @Test
    void acceptsMissingOptionalFieldsAndUnknownFields() throws IOException {
        // Given TMDB returns a sparse payload containing a future unknown field.
        expectMovieRequest().andRespond(withSuccess(read("tmdb/movie-sparse.json"), MediaType.APPLICATION_JSON));

        // When the sparse movie response is requested.
        var movie = movieClient.getMovie(603L);

        // Then optional, nullable, empty, and unknown values are handled safely.
        assertThat(movie.id()).isEqualTo(603);
        assertThat(movie.backdropPath()).isNull();
        assertThat(movie.releaseDate()).isEmpty();
        assertThat(movie.credits()).isNull();
    }

    @Test
    void propagatesTmdbHttpErrors() {
        // Given TMDB cannot find the requested movie.
        expectMovieRequest().andRespond(withStatus(HttpStatus.NOT_FOUND));

        // When the movie is requested, then the HTTP status remains available to the caller.
        assertThatThrownBy(() -> movieClient.getMovie(603L))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsMalformedTmdbResponses() {
        // Given TMDB returns malformed JSON.
        expectMovieRequest().andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        // When the movie is requested, then deserialization fails visibly.
        assertThatThrownBy(() -> movieClient.getMovie(603L)).isInstanceOf(RestClientException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidMovieIdsWithoutCallingTmdb(Long id) {
        // Given an invalid TMDB movie ID, when it is requested, then it is rejected locally.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> movieClient.getMovie(id))
                .withMessage("TMDB movie ID must be positive");
    }

    private ResponseActions expectMovieRequest() {
        return server.expect(requestTo(MOVIE_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN));
    }

}
