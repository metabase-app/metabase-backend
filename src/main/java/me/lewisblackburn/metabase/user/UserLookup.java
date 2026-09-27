package me.lewisblackburn.metabase.user;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLookup {
    private final UserRepository userRepository;

    /** Use for actions that require an existing account that has not been soft-deleted. */
    public User requireActiveUser(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        User user = userRepository.find(userId);
        if (user == null || user.deletedAt() != null) {
            throw new IllegalArgumentException("User not found");
        }
        return user;
    }
}
