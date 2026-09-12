package me.lewisblackburn.metabase.integrations.tmdb;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.integrations.tmdb.dto.TmdbMovieDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class TmdbMovieClient {

    private static final String APPENDED_RESOURCES = "credits,external_ids";

    @Qualifier("tmdbRestClient")
    private final RestClient restClient;

    public TmdbMovieDto getMovie(long id) {
        Assert.isTrue(id > 0, "TMDB movie ID must be positive");

        return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{id}")
                        .queryParam("append_to_response", APPENDED_RESOURCES)
                        .build(id))
                .retrieve()
                .body(TmdbMovieDto.class);
    }
}
