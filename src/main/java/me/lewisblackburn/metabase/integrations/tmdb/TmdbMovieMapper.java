package me.lewisblackburn.metabase.integrations.tmdb;

import me.lewisblackburn.metabase.integrations.support.ProviderRuntimes;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.MovieMapper;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import me.lewisblackburn.metabase.util.DateUtils;
import me.lewisblackburn.metabase.util.TextUtils;
import org.springframework.stereotype.Component;

@Component
public class TmdbMovieMapper implements MovieMapper<TmdbMovieDto> {
    @Override
    public MovieImportData map(TmdbMovieDto source) {
        if (source.title() == null || source.title().isBlank()) {
            throw new IllegalArgumentException("Movie title must not be blank");
        }

        return new MovieImportData(source.title(), TextUtils.blankToNull(source.overview()),
                TextUtils.blankToNull(source.originalTitle()),
                TextUtils.blankToNull(source.originalLanguage()),
                DateUtils.parseOptionalLocalDate(source.releaseDate()),
                ProviderRuntimes.normalizeRuntime(source.runtime()));
    }
}
