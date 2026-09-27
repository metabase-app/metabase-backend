package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record TmdbProductionCountryDto(
        @JsonProperty("iso_3166_1") String countryCode,
        String name
) {
}
