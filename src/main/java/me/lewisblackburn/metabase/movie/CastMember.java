package me.lewisblackburn.metabase.movie;

public record CastMember(
        Long personId,
        String name,
        String character,
        Integer order) {
}

