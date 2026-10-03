package me.lewisblackburn.metabase.user;

import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;

public interface UserRepository {
    List<User> findAll();

    User find(Long id);

    boolean follow(Long followerId, Long followedId);

    boolean unfollow(Long followerId, Long followedId);

    Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds);

    List<User> findFollowers(Long userId, int offset, int limit);

    List<User> findFollowing(Long userId, int offset, int limit);
}
