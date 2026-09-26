package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbCrewMemberDto(
        Boolean adult,
        Integer gender,
        Long id,
        String knownForDepartment,
        String name,
        String originalName,
        Double popularity,
        String profilePath,
        String creditId,
        String department,
        String job
) {
}
