package me.lewisblackburn.metabase.movie.model;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record MovieImportData(
        String title,
        String overview,
        String originalTitle,
        String originalLanguageCode,
        LocalDate releaseDate,
        Integer runtimeMinutes
) {
}
