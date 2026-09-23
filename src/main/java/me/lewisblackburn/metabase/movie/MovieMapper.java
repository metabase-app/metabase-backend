package me.lewisblackburn.metabase.movie;

import me.lewisblackburn.metabase.movie.model.MovieImportData;

public interface MovieMapper<T> {
    MovieImportData map(T source);

}
