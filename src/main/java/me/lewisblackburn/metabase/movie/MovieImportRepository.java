package me.lewisblackburn.metabase.movie;

import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.model.MovieImportData;

public interface MovieImportRepository {
    Movie save(String sourceName, String providerEntityType, String externalId,
            MovieImportData data);
}
