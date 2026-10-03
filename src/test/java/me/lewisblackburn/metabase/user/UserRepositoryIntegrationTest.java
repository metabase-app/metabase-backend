package me.lewisblackburn.metabase.user;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;
import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.UserFollows.USER_FOLLOWS;
import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class UserRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFollowService followService;

    @Test
    void followsAndUnfollowsIdempotentlyWithoutChangingOtherRelationships() {
        // Given three users who can follow one another.
        Long alice = insertUser("follow_alice");
        Long bob = insertUser("follow_bob");
        Long carol = insertUser("follow_carol");

        // When Alice follows Bob twice, only the first request changes the relationship.
        followService.follow(carol, bob);
        followService.follow(bob, alice);
        assertThat(followService.follow(alice, bob).id()).isEqualTo(bob);
        followService.follow(alice, bob);
        assertThat(dsl.fetchCount(USER_FOLLOWS,
                USER_FOLLOWS.FOLLOWER_ID.eq(alice).and(USER_FOLLOWS.FOLLOWED_ID.eq(bob))))
                .isEqualTo(1);
        assertThat(userRepository.findFollowing(alice, 0, 20))
                .extracting(User::id).containsExactly(bob);
        assertThat(dsl.select(AUDIT_EVENTS.ACTION, AUDIT_EVENTS.ACTOR_USER_ID,
                AUDIT_EVENTS.DETAILS)
                .from(AUDIT_EVENTS)
                .where(AUDIT_EVENTS.ACTOR_USER_ID.eq(alice))
                .fetch()).singleElement().satisfies(event -> {
                    assertThat(event.get(AUDIT_EVENTS.ACTION)).isEqualTo("USER_FOLLOWED");
                    assertThat(event.get(AUDIT_EVENTS.DETAILS))
                            .isEqualTo(JSONB.valueOf("{\"followedUserId\":" + bob + "}"));
                });

        // When Alice unfollows Bob twice, only the first request removes the relationship.
        assertThat(followService.unfollow(alice, bob).id()).isEqualTo(bob);
        followService.unfollow(alice, bob);
        assertThat(userRepository.findFollowing(alice, 0, 20)).isEmpty();
        assertThat(dsl.fetchCount(USER_FOLLOWS)).isEqualTo(2);
        assertThat(dsl.select(AUDIT_EVENTS.ACTION)
                .from(AUDIT_EVENTS)
                .where(AUDIT_EVENTS.ACTOR_USER_ID.eq(alice))
                .orderBy(AUDIT_EVENTS.OCCURRED_AT)
                .fetch(AUDIT_EVENTS.ACTION))
                .containsExactly("USER_FOLLOWED", "USER_UNFOLLOWED");
    }

    @Test
    void rejectsSelfMissingAndDeletedUsersWithoutWritingRelationships() {
        Long alice = insertUser("invalid_alice");
        Long deleted = insertUser("deleted_target");
        dsl.update(USERS).set(USERS.DELETED_AT, java.time.OffsetDateTime.now())
                .where(USERS.ID.eq(deleted)).execute();

        for (Long target : List.of(alice, deleted, Long.MAX_VALUE, 0L, -1L)) {
            assertThatThrownBy(() -> followService.follow(alice, target))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> followService.unfollow(alice, target))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> followService.follow(deleted, alice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dsl.fetchCount(USER_FOLLOWS)).isZero();
    }

    @Autowired
    private DSLContext dsl;

    @Test
    void groupsRolesForOnlyTheRequestedUsers() {
        // Given users have different roles, including one user with no assigned roles.
        Long adminId = insertUser("admin_user");
        Long memberId = insertUser("member_user");
        Long noRolesId = insertUser("no_roles_user");
        Long excludedId = insertUser("excluded_user");
        dsl.insertInto(USER_ROLES, USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .values(adminId, "ADMIN")
                .values(adminId, "USER")
                .values(memberId, "USER")
                .values(excludedId, "ADMIN")
                .execute();

        // When the requested users' roles are loaded in a batch.
        var roles = userRepository.findRolesByUserIds(List.of(adminId, memberId, noRolesId));

        // Then roles are grouped by user and unrelated users are excluded.
        assertThat(roles).containsOnlyKeys(adminId, memberId);
        assertThat(roles.get(adminId)).containsExactly(Role.ADMIN, Role.USER);
        assertThat(roles.get(memberId)).containsExactly(Role.USER);
        assertThat(roles.getOrDefault(noRolesId, List.of())).isEmpty();
    }

    @Test
    void returnsNoRolesForAnEmptyBatch() {
        // Given there are no user IDs to resolve.
        // When an empty batch is requested.
        // Then there are no role groups to return.
        assertThat(userRepository.findRolesByUserIds(List.of())).isEmpty();
    }

    @Test
    void paginatesFollowersAndFollowingInTheirRespectiveDirections() {
        // Given Alice follows Bob and Carol, Carol follows Bob, and Bob follows Carol.
        Long alice = insertUser("alice");
        Long bob = insertUser("bob");
        Long carol = insertUser("carol");
        Long isolated = insertUser("isolated");
        Long unrelated = insertUser("unrelated");
        dsl.insertInto(USER_FOLLOWS, USER_FOLLOWS.FOLLOWER_ID, USER_FOLLOWS.FOLLOWED_ID)
                .values(alice, carol)
                .values(alice, bob)
                .values(carol, bob)
                .values(bob, carol)
                .values(unrelated, alice)
                .execute();

        // When followers are read at two offsets.
        var firstPage = userRepository.findFollowers(bob, 0, 1);
        var secondPage = userRepository.findFollowers(bob, 1, 1);

        // Then each page contains the correct follower without unrelated users.
        assertThat(firstPage).extracting(User::id).containsExactly(alice);
        assertThat(secondPage).extracting(User::id).containsExactly(carol);

        // When following users are read at two offsets.
        var firstFollowing = userRepository.findFollowing(alice, 0, 1);
        var secondFollowing = userRepository.findFollowing(alice, 1, 1);

        // Then the full user mapping and relationship direction are preserved.
        assertThat(firstFollowing).containsExactly(userRepository.find(bob));
        assertThat(secondFollowing).containsExactly(userRepository.find(carol));
        assertThat(userRepository.findFollowers(carol, 0, 20))
                .extracting(User::id).containsExactly(alice, bob);
        assertThat(userRepository.findFollowing(bob, 0, 20))
                .extracting(User::id).containsExactly(carol);

        // When the offset is beyond the last follower or the relationship list is empty.
        var emptyFollowers = userRepository.findFollowers(isolated, 0, 20);
        var emptyFollowing = userRepository.findFollowing(isolated, 0, 20);
        var pastEnd = userRepository.findFollowers(bob, 2, 1);

        // Then those pages are empty.
        assertThat(emptyFollowers).isEmpty();
        assertThat(emptyFollowing).isEmpty();
        assertThat(pastEnd).isEmpty();

        // When the default limit is supplied, both followed users are returned.
        assertThat(userRepository.findFollowing(alice, 0, 20))
                .extracting(User::id).containsExactly(bob, carol);
    }

    @Test
    void rejectsInvalidPaginationArguments() {
        // Given a user whose follow list can be requested.
        Long userId = insertUser("page_user");

        // When offsets or limits are outside their allowed ranges.
        // Then invalid requests fail before querying relationships.
        for (int limit : List.of(-1, 0, 101, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> userRepository.findFollowers(userId, 0, limit))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> userRepository.findFollowing(userId, 0, limit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> userRepository.findFollowers(userId, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userRepository.findFollowing(userId, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long insertUser(String username) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.DISPLAY_NAME, username)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }

}
