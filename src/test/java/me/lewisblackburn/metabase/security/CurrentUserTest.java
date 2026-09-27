package me.lewisblackburn.metabase.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CurrentUserTest {
    private final CurrentUser currentUser = new CurrentUser();

    @Test
    void returnsTheStableIdFromThePrincipal() {
        UserPrincipal principal = UserPrincipal.principalBuilder()
                .id(42L)
                .username("alice")
                .passwordHash("")
                .build();

        assertThat(currentUser.requireUserId(principal)).isEqualTo(42L);
    }

    @Test
    void rejectsAMissingPrincipal() {
        assertThatThrownBy(() -> currentUser.requireUserId(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("A verified user identity is required");
    }
}
