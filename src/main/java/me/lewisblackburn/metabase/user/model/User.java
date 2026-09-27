package me.lewisblackburn.metabase.user.model;

import java.time.OffsetDateTime;
import java.util.List;
import graphql.relay.Connection;
import lombok.Builder;

@Builder
public record User(
        Long id,
        String username,
        String email,
        List<Role> roles,
        Connection<User> following,
        Connection<User> followers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime deletedAt
) {

}
