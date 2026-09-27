package me.lewisblackburn.metabase.integrations.tmdb;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Builder;

@ConfigurationProperties("metabase.tmdb")
@Builder
public record TmdbProperties(
        URI baseUrl,
        String accessToken
) {
}
