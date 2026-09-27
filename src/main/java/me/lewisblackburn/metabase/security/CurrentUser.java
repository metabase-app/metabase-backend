package me.lewisblackburn.metabase.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    /** Authentication is checked by @PreAuthorize; this requires our ID-bearing principal. */
    public Long requireUserId(UserPrincipal principal) {
        // Spring can authenticate another principal type, which resolves to null here.
        if (principal == null) {
            throw new AccessDeniedException("A verified user identity is required");
        }
        return principal.id();
    }
}
