package me.lewisblackburn.metabase.movie;

import me.lewisblackburn.metabase.movie.model.Movie;

public interface MovieImportService<T> {
    Movie importMovie(T externalId);
}
