package me.lewisblackburn.metabase.user;

import static me.lewisblackburn.metabase.jooq.tables.AuditEvents.AUDIT_EVENTS;
import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.UserFollows.USER_FOLLOWS;
import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.Subrange;
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
        assertThat(userRepository.findFollowing(alice, page(20, null)).getContent())
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
        assertThat(userRepository.findFollowing(alice, page(20, null)).getContent()).isEmpty();
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

        // When follower windows are requested using the previous window's keyset position.
        var firstPage = userRepository.findFollowers(bob, page(1, null));
        var secondPage = userRepository.findFollowers(bob,
                new Subrange<>(firstPage.positionAt(0), 1, true));

        // Then each window contains the correct followers without duplication or unrelated users.
        assertThat(firstPage.getContent()).extracting(User::id).containsExactly(alice);
        assertThat(firstPage.positionAt(0)).isEqualTo(ScrollPosition.forward(Map.of("id", alice)));
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).extracting(User::id).containsExactly(carol);
        assertThat(secondPage.hasNext()).isFalse();

        // When the following direction is paginated using the same Spring types.
        var firstFollowing = userRepository.findFollowing(alice, page(1, null));
        var secondFollowing = userRepository.findFollowing(alice,
                new Subrange<>(firstFollowing.positionAt(0), 1, true));

        // Then the full user mapping and relationship direction are preserved.
        assertThat(firstFollowing.getContent()).containsExactly(userRepository.find(bob));
        assertThat(firstFollowing.hasNext()).isTrue();
        assertThat(secondFollowing.getContent()).containsExactly(userRepository.find(carol));
        assertThat(secondFollowing.hasNext()).isFalse();
        assertThat(userRepository.findFollowers(carol, page(20, null)).getContent())
                .extracting(User::id).containsExactly(alice, bob);
        assertThat(userRepository.findFollowing(bob, page(20, null)).getContent())
                .extracting(User::id).containsExactly(carol);

        // When a window is empty or the cursor is beyond the last follower.
        var emptyFollowers = userRepository.findFollowers(isolated, page(20, null));
        var emptyFollowing = userRepository.findFollowing(isolated, page(20, null));
        var pastEnd = userRepository.findFollowers(bob, page(1, carol));

        // Then empty windows do not report further results.
        for (var window : List.of(emptyFollowers, emptyFollowing, pastEnd)) {
            assertThat(window.getContent()).isEmpty();
            assertThat(window.hasNext()).isFalse();
        }

        // When the count is omitted, the common SQL adapter supplies twenty rows per window.
        assertThat(userRepository.findFollowing(alice, page(null, null)).getContent())
                .extracting(User::id).containsExactly(bob, carol);
    }

    @Test
    void rejectsInvalidPaginationArguments() {
        // Given a user whose follow list can be requested.
        Long userId = insertUser("page_user");

        // When counts exceed the bounds or IDs are not positive.
        // Then invalid requests fail before querying relationships.
        for (int first : List.of(-1, 101, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> userRepository.findFollowers(userId, page(first, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> userRepository.findFollowing(userId, page(first, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> userRepository.findFollowers(userId, page(20, 0L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userRepository.findFollowing(userId, page(20, -1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userRepository.findFollowing(userId,
                new Subrange<>(ScrollPosition.offset(5L), 20, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userRepository.findFollowing(userId,
                new Subrange<>(ScrollPosition.keyset(), 20, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long insertUser(String username) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.DISPLAY_NAME, username)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }

    private Subrange<ScrollPosition> page(Integer first, Long after) {
        return new Subrange<>(after == null ? null : ScrollPosition.forward(Map.of("id", after)),
                first, true);
    }
}
