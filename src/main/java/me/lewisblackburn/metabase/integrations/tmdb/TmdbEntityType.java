package me.lewisblackburn.metabase.integrations.tmdb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TmdbEntityType {
    MOVIE("MOVIE");

    private final String databaseValue;
}
