package me.lewisblackburn.metabase.user;

import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFollowService {
    private final UserRepository userRepository;
    private final UserLookup userLookup;

    @Transactional
    public User follow(Long followerId, Long followedId) {
        User target = validateUsers(followerId, followedId);
        userRepository.follow(followerId, followedId);
        return target;
    }

    @Transactional
    public User unfollow(Long followerId, Long followedId) {
        User target = validateUsers(followerId, followedId);
        userRepository.unfollow(followerId, followedId);
        return target;
    }

    private User validateUsers(Long followerId, Long followedId) {
        if (followerId != null && followerId.equals(followedId)) {
            throw new IllegalArgumentException("You cannot follow or unfollow yourself");
        }
        // Recheck the acting account too: it may have been deleted since the session started.
        userLookup.requireActiveUser(followerId);
        return userLookup.requireActiveUser(followedId);
    }
}
