package me.lewisblackburn.metabase.user.model;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record User(
        Long id,
        String username,
        String email,
        List<Role> roles,
        List<User> following,
        List<User> followers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime deletedAt
) {

}
