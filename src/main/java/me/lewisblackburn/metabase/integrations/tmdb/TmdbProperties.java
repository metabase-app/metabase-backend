package me.lewisblackburn.metabase.integrations.tmdb;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("metabase.tmdb")
public record TmdbProperties(
        URI baseUrl,
        String accessToken
) {
}
