package me.lewisblackburn.metabase.integrations.tmdb;

import static me.lewisblackburn.metabase.support.ClasspathFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TmdbMovieMapperTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final TmdbMovieMapper mapper = new TmdbMovieMapper();

    @Test
    void mapsCompleteMovie() throws IOException {
        // Given TMDB supplies a complete movie payload.
        TmdbMovieDto source = jsonMapper.readValue(read("tmdb/movie-603.json"), TmdbMovieDto.class);

        // When the movie is mapped into internal import data.
        MovieImportData result = mapper.map(source);

        // Then all supported fields are mapped with the parsed release date.
        assertThat(result).isEqualTo(
                new MovieImportData("The Matrix", "A computer hacker discovers the truth.",
                        "The Matrix", "en", LocalDate.of(1999, 3, 30), 136));
    }

    @Test
    void mapsSparseMovie() throws IOException {
        // Given TMDB supplies a title but omits optional fields and leaves the date empty.
        TmdbMovieDto source =
                jsonMapper.readValue(read("tmdb/movie-sparse.json"), TmdbMovieDto.class);

        // When the sparse movie is mapped.
        MovieImportData result = mapper.map(source);

        // Then the title is preserved and unknown values remain absent.
        assertThat(result)
                .isEqualTo(new MovieImportData("The Matrix", null, null, null, null, null));
    }

    @Test
    void normalizesBlankFieldsAndZeroRuntime() {
        // Given TMDB supplies blank optional text and a zero runtime.
        TmdbMovieDto source = jsonMapper.readValue("""
                {
                  "title": "The Matrix",
                  "overview": " ",
                  "original_title": "",
                  "original_language": "  ",
                  "release_date": " ",
                  "runtime": 0
                }
                """, TmdbMovieDto.class);

        // When the movie is mapped.
        MovieImportData result = mapper.map(source);

        // Then blank values and zero runtime become absent without changing the source.
        assertThat(result)
                .isEqualTo(new MovieImportData("The Matrix", null, null, null, null, null));
        assertThat(source.overview()).isEqualTo(" ");
        assertThat(source.originalTitle()).isEmpty();
        assertThat(source.originalLanguage()).isEqualTo("  ");
        assertThat(source.runtime()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankTitle(String title) {
        // Given TMDB supplies a missing or blank title.
        var payload = jsonMapper.createObjectNode().put("title", title);
        TmdbMovieDto source = jsonMapper.treeToValue(payload, TmdbMovieDto.class);

        // When mapping is attempted, then the required title is rejected clearly.
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.map(source))
                .withMessage("Movie title must not be blank");
    }

    @Test
    void rejectsNegativeRuntime() {
        // Given TMDB supplies a valid title with an invalid negative runtime.
        TmdbMovieDto source = jsonMapper.readValue("""
                {"title": "The Matrix", "runtime": -1}
                """, TmdbMovieDto.class);

        // When mapping is attempted, then the runtime validation failure is propagated.
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.map(source))
                .withMessage("Runtime must not be negative");
    }

    @Test
    void rejectsInvalidReleaseDate() {
        // Given TMDB supplies a nonblank release date that is not a real calendar date.
        TmdbMovieDto source = jsonMapper.readValue("""
                {"title": "The Matrix", "release_date": "2023-02-29"}
                """, TmdbMovieDto.class);

        // When mapping is attempted, then the date error is propagated instead of losing the value
        // silently.
        assertThatThrownBy(() -> mapper.map(source)).isInstanceOf(DateTimeParseException.class);
    }
}
