package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbPersonDto(
        Boolean adult,
        List<String> alsoKnownAs,
        String biography,
        String birthday,
        String deathday,
        Integer gender,
        String homepage,
        Long id,
        String imdbId,
        String knownForDepartment,
        String name,
        String originalName,
        String placeOfBirth,
        Double popularity,
        String profilePath) {}
