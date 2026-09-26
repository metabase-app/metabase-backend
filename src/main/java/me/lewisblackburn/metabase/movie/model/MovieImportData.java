package me.lewisblackburn.metabase.movie.model;

import java.time.LocalDate;

public record MovieImportData(
        String title,
        String overview,
        String originalTitle,
        String originalLanguageCode,
        LocalDate releaseDate,
        Integer runtimeMinutes
) {
}
