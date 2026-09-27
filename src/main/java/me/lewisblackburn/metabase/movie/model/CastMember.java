package me.lewisblackburn.metabase.movie.model;

import lombok.Builder;

@Builder
public record CastMember(
        Long personId,
        String name,
        String character,
        Integer order
) {
}
