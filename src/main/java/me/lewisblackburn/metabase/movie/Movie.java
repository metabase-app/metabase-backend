package me.lewisblackburn.metabase.movie;

import java.time.LocalDate;

public record Movie(
        Long id,
        String title,
        String overview,
        LocalDate releaseDate,
        Integer runtimeMinutes) {
}

