package me.lewisblackburn.metabase.user;

import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import org.springframework.data.domain.Window;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.Subrange;

public interface UserRepository {
    List<User> findAll();

    User find(Long id);

    void follow(Long followerId, Long followedId);

    void unfollow(Long followerId, Long followedId);

    Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds);

    Window<User> findFollowers(Long userId, Subrange<ScrollPosition> page);

    Window<User> findFollowing(Long userId, Subrange<ScrollPosition> page);
}
