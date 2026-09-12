package me.lewisblackburn.metabase.integrations.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenreDto(Long id, String name) {}
