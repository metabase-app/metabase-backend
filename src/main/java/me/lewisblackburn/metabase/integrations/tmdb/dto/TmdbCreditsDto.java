package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record TmdbCreditsDto(
        Long id,
        List<TmdbCastMemberDto> cast,
        List<TmdbCrewMemberDto> crew
) {
}
