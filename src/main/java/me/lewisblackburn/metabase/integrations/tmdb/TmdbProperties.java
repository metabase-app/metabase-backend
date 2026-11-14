package me.lewisblackburn.metabase.integrations.tmdb;

import java.net.URI;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("metabase.tmdb")
@Builder
public record TmdbProperties(URI baseUrl, String accessToken) {}
