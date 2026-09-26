package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbMovieDto(
        Boolean adult,
        String backdropPath,
        BelongsToCollection belongsToCollection,
        Long budget,
        List<TmdbGenreDto> genres,
        String homepage,
        Long id,
        String imdbId,
        List<String> originCountry,
        String originalLanguage,
        String originalTitle,
        String overview,
        Double popularity,
        String posterPath,
        List<TmdbProductionCompanyDto> productionCompanies,
        List<TmdbProductionCountryDto> productionCountries,
        String releaseDate,
        Long revenue,
        Integer runtime,
        List<TmdbSpokenLanguageDto> spokenLanguages,
        String status,
        String tagline,
        String title,
        Boolean video,
        Double voteAverage,
        Integer voteCount,
        TmdbCreditsDto credits,
        ExternalIds externalIds
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BelongsToCollection(
            Long id,
            String name,
            String posterPath,
            String backdropPath
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExternalIds(
            String imdbId,
            String wikidataId,
            String facebookId,
            String instagramId,
            String twitterId
    ) {
    }
}
