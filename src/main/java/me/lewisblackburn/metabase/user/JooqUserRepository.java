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
import org.springframework.data.domain.Window;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.Subrange;
import me.lewisblackburn.metabase.pagination.JooqPagination;

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
    public Window<User> findFollowers(Long userId, Subrange<ScrollPosition> page) {
        return JooqPagination.fetch(dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL,
                        USERS.CREATED_AT, USERS.UPDATED_AT, USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USER_FOLLOWS)
                .join(USERS).on(USERS.ID.eq(USER_FOLLOWS.FOLLOWER_ID))
                .where(USER_FOLLOWS.FOLLOWED_ID.eq(userId)), USERS.ID, page, this::mapUser);
    }

    @Override
    public Window<User> findFollowing(Long userId, Subrange<ScrollPosition> page) {
        return JooqPagination.fetch(dsl
                .select(USERS.ID, USERS.USERNAME, USERS.EMAIL,
                        USERS.CREATED_AT, USERS.UPDATED_AT, USERS.LAST_SEEN_AT, USERS.DELETED_AT)
                .from(USER_FOLLOWS)
                .join(USERS).on(USERS.ID.eq(USER_FOLLOWS.FOLLOWED_ID))
                .where(USER_FOLLOWS.FOLLOWER_ID.eq(userId)), USERS.ID, page, this::mapUser);
    }

    private User mapUser(Record record) {
        return User.builder()
                .id(record.get(USERS.ID))
                .username(record.get(USERS.USERNAME))
                .email(record.get(USERS.EMAIL))
                .roles(List.of())
                .following(JooqPagination.empty())
                .followers(JooqPagination.empty())
                .createdAt(record.get(USERS.CREATED_AT))
                .updatedAt(record.get(USERS.UPDATED_AT))
                .lastSeenAt(record.get(USERS.LAST_SEEN_AT))
                .deletedAt(record.get(USERS.DELETED_AT))
                .build();
    }
}
