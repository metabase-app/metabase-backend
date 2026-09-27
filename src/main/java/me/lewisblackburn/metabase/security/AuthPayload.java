package me.lewisblackburn.metabase.security;

import lombok.Builder;

@Builder
public record AuthPayload(
        Long id,
        String username
) {
}
