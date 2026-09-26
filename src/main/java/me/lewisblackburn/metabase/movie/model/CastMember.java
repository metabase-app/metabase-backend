package me.lewisblackburn.metabase.movie.model;

public record CastMember(
        Long personId,
        String name,
        String character,
        Integer order
) {
}
