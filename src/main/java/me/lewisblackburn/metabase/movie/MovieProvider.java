package me.lewisblackburn.metabase.movie;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MovieProvider {
    TMDB("TMDB");

    private final String databaseValue;
}
