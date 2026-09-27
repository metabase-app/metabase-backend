package me.lewisblackburn.metabase.user;

import java.util.List;
import java.util.Map;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import graphql.relay.Connection;
import me.lewisblackburn.metabase.pagination.CursorPageRequest;

public interface UserRepository {
    List<User> findAll();

    User find(Long id);

    Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds);

    Connection<User> findFollowers(Long userId, CursorPageRequest page);

    Connection<User> findFollowing(Long userId, CursorPageRequest page);
}
