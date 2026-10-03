package me.lewisblackburn.metabase.user;

import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static me.lewisblackburn.metabase.jooq.tables.UserFollows.USER_FOLLOWS;
import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import me.lewisblackburn.metabase.pagination.Pagination;

@Repository
@RequiredArgsConstructor
public class JooqUserRepository implements UserRepository {
    private final DSLContext dsl;

    @Override
    public List<User> findAll() {
        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.CREATED_AT,
                        USERS.UPDATED_AT,
                        USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USERS).orderBy(USERS.USERNAME)
                .fetch(this::mapUser);
    }

    @Override
    public User find(Long id) {
        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL, USERS.CREATED_AT,
                        USERS.UPDATED_AT,
                        USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USERS).where(USERS.ID.eq(id))
                .fetchOne(this::mapUser);
    }


    @Override
    public boolean follow(Long followerId, Long followedId) {
        // The unique pair and doNothing make repeated follow requests safe to retry.
        return dsl.insertInto(USER_FOLLOWS)
                .set(USER_FOLLOWS.FOLLOWER_ID, followerId)
                .set(USER_FOLLOWS.FOLLOWED_ID, followedId)
                .onConflict(USER_FOLLOWS.FOLLOWER_ID, USER_FOLLOWS.FOLLOWED_ID)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public boolean unfollow(Long followerId, Long followedId) {
        return dsl.deleteFrom(USER_FOLLOWS)
                .where(USER_FOLLOWS.FOLLOWER_ID.eq(followerId))
                .and(USER_FOLLOWS.FOLLOWED_ID.eq(followedId))
                .execute() == 1;
    }

    @Override
    public Map<Long, List<Role>> findRolesByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return dsl
                .select(USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.in(userIds))
                .orderBy(USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .fetchGroups(USER_ROLES.USER_ID,
                        record -> Role.valueOf(record.get(USER_ROLES.ROLE)));
    }

    @Override
    public List<User> findFollowers(Long userId, int offset, int limit) {
        Pagination.validate(offset, limit);
        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL,
                        USERS.CREATED_AT, USERS.UPDATED_AT, USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USER_FOLLOWS)
                .join(USERS).on(USERS.ID.eq(USER_FOLLOWS.FOLLOWER_ID))
                .where(USER_FOLLOWS.FOLLOWED_ID.eq(userId))
                .orderBy(USERS.ID)
                .offset(offset).limit(limit)
                .fetch(this::mapUser);
    }

    @Override
    public List<User> findFollowing(Long userId, int offset, int limit) {
        Pagination.validate(offset, limit);
        return dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL,
                        USERS.CREATED_AT, USERS.UPDATED_AT, USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USER_FOLLOWS)
                .join(USERS).on(USERS.ID.eq(USER_FOLLOWS.FOLLOWED_ID))
                .where(USER_FOLLOWS.FOLLOWER_ID.eq(userId))
                .orderBy(USERS.ID)
                .offset(offset).limit(limit)
                .fetch(this::mapUser);
    }

    private User mapUser(Record record) {
        return User.builder()
                .id(record.get(USERS.ID))
                .username(record.get(USERS.USERNAME))
                .email(record.get(USERS.EMAIL))
                .roles(List.of())
                .following(List.of())
                .followers(List.of())
                .createdAt(record.get(USERS.CREATED_AT))
                .updatedAt(record.get(USERS.UPDATED_AT))
                .lastSeenAt(record.get(USERS.LAST_SEEN_AT))
                .deletedAt(record.get(USERS.DELETED_AT))
                .build();
    }
}
