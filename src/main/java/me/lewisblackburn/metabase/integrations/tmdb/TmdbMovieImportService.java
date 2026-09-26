package me.lewisblackburn.metabase.integrations.tmdb;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import me.lewisblackburn.metabase.movie.JooqMovieImportRepository;
import me.lewisblackburn.metabase.movie.MovieImportService;
import me.lewisblackburn.metabase.movie.MovieProvider;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class TmdbMovieImportService implements MovieImportService<Long> {

    private final TmdbMovieClient client;
    private final TmdbMovieMapper mapper;
    private final JooqMovieImportRepository repository;

    @Override
    public Movie importMovie(Long externalId) {
        Assert.isTrue(externalId != null && externalId > 0, "TMDB movie ID must be positive");
        TmdbMovieDto movie = client.getMovie(externalId);
        Assert.state(movie != null && externalId.equals(movie.id()),
                "TMDB returned an unexpected movie");
        MovieImportData data = mapper.map(movie);
        return repository.save(MovieProvider.TMDB.getDatabaseValue(),
                TmdbEntityType.MOVIE.getDatabaseValue(), externalId.toString(), data);
    }
}
