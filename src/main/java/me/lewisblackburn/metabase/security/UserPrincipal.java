package me.lewisblackburn.metabase.security;

import java.io.Serial;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring's user details extended with our stable account ID. */
public final class UserPrincipal extends User implements Principal {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;

    @Builder(builderMethodName = "principalBuilder")
    private UserPrincipal(Long id, String username, String passwordHash,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, Objects.requireNonNull(passwordHash, "Password hash is required"),
                authorities == null ? List.of() : authorities);
        this.id = Objects.requireNonNull(id, "User ID is required");
        if (id < 1) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }

    public Long id() {
        return id;
    }

    @Override
    public String getName() {
        return getUsername();
    }
}
