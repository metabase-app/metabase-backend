package me.lewisblackburn.metabase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import me.lewisblackburn.metabase.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserLookupTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserLookup userLookup = new UserLookup(userRepository);

    @Test
    void returnsAnActiveUser() {
        User user = User.builder().id(42L).username("alice").build();
        when(userRepository.find(42L)).thenReturn(user);

        assertThat(userLookup.requireActiveUser(42L)).isSameAs(user);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsBeforeQueryingTheDatabase(Long userId) {
        assertThatThrownBy(() -> userLookup.requireActiveUser(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID must be positive");
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsAMissingUser() {
        assertThatThrownBy(() -> userLookup.requireActiveUser(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    void rejectsADeletedUser() {
        when(userRepository.find(42L)).thenReturn(User.builder()
                .id(42L)
                .username("alice")
                .deletedAt(OffsetDateTime.now())
                .build());

        assertThatThrownBy(() -> userLookup.requireActiveUser(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }
}
