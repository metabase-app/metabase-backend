package me.lewisblackburn.metabase.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import lombok.Builder;

@Builder
public record UserPrincipal(
        Long id,
        String username
) implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserPrincipal {
        Objects.requireNonNull(id, "User ID is required");
        if (id < 1) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
    }

    @Override
    public String getName() {
        return username;
    }
}
