package me.lewisblackburn.metabase.movie.model;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record Movie(
        Long id,
        String title,
        String overview,
        LocalDate releaseDate,
        Integer runtimeMinutes
) {
}
