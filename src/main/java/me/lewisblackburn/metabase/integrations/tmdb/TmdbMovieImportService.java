package me.lewisblackburn.metabase.integrations.tmdb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.lewisblackburn.metabase.event.ImportEvents;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.JooqMovieImportRepository;
import me.lewisblackburn.metabase.movie.MovieImportService;
import me.lewisblackburn.metabase.movie.MovieProvider;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbMovieImportService implements MovieImportService<Long> {

    private final TmdbMovieClient client;
    private final TmdbMovieMapper mapper;
    private final JooqMovieImportRepository repository;
    private final ImportEvents importEvents;

    @Override
    public Movie importMovie(Long externalId) {
        Assert.isTrue(externalId != null && externalId > 0, "TMDB movie ID must be positive");
        try {
            TmdbMovieDto movie = client.getMovie(externalId);
            Assert.state(movie != null && externalId.equals(movie.id()),
                    "TMDB returned an unexpected movie");
            MovieImportData data = mapper.map(movie);
            return repository.save(MovieProvider.TMDB.getDatabaseValue(),
                    TmdbEntityType.MOVIE.getDatabaseValue(), externalId.toString(), data);
        } catch (RuntimeException failure) {
            try {
                importEvents.failed(MovieProvider.TMDB.getDatabaseValue(),
                        TmdbEntityType.MOVIE.getDatabaseValue(), externalId.toString(), failure);
            } catch (RuntimeException recordingFailure) {
                log.error("Could not record TMDB import failure for movie {} ({})", externalId,
                        recordingFailure.getClass().getSimpleName());
            }
            throw failure;
        }
    }
}
