package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record TmdbGenreDto(
        Long id,
        String name
) {
}
