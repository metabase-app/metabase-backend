package me.lewisblackburn.metabase.user;

import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.UserFollows.USER_FOLLOWS;
import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import graphql.relay.Edge;
import me.lewisblackburn.metabase.pagination.IdCursor;
import me.lewisblackburn.metabase.pagination.CursorPageRequest;
import me.lewisblackburn.metabase.user.model.Role;
import me.lewisblackburn.metabase.user.model.User;
import org.jooq.DSLContext;
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

        // When one follower is requested at a time using the preceding page's cursor.
        var firstPage = userRepository.findFollowers(bob, page(1, null));
        var secondPage =
                userRepository.findFollowers(bob,
                        page(1, firstPage.getPageInfo().getEndCursor().getValue()));

        // Then pages contain only Bob's followers in ID order without overlap.
        assertThat(firstPage.getEdges().stream().map(Edge::getNode).toList()).extracting(User::id)
                .containsExactly(alice);
        assertThat(firstPage.getPageInfo().getEndCursor().getValue())
                .isEqualTo(IdCursor.encode(alice));
        assertThat(firstPage.getPageInfo().isHasNextPage()).isTrue();
        assertThat(secondPage.getEdges().stream().map(Edge::getNode).toList()).extracting(User::id)
                .containsExactly(carol);
        assertThat(secondPage.getPageInfo().getEndCursor().getValue())
                .isEqualTo(IdCursor.encode(carol));
        assertThat(secondPage.getPageInfo().isHasNextPage()).isFalse();

        // When Alice's following list is requested using the same pagination rules.
        var firstFollowing = userRepository.findFollowing(alice, page(1, null));
        var secondFollowing =
                userRepository.findFollowing(alice,
                        page(1, firstFollowing.getPageInfo().getEndCursor().getValue()));

        // Then the direction is correct and every node has the complete user mapping.
        assertThat(firstFollowing.getEdges().stream().map(Edge::getNode).toList())
                .containsExactly(userRepository.find(bob));
        assertThat(firstFollowing.getPageInfo().isHasNextPage()).isTrue();
        assertThat(secondFollowing.getEdges().stream().map(Edge::getNode).toList())
                .containsExactly(userRepository.find(carol));
        assertThat(secondFollowing.getPageInfo().isHasNextPage()).isFalse();

        // When Relay's valid zero-sized page is requested, it still reports remaining results.
        var zeroPage = userRepository.findFollowing(alice, page(0, null));
        assertThat(zeroPage.getEdges()).isEmpty();
        assertThat(zeroPage.getPageInfo().getStartCursor()).isNull();
        assertThat(zeroPage.getPageInfo().getEndCursor()).isNull();
        assertThat(zeroPage.getPageInfo().isHasNextPage()).isTrue();
        assertThat(userRepository.findFollowers(carol, page(20, null)).getEdges().stream()
                .map(Edge::getNode).toList())
                .extracting(User::id).containsExactly(alice, bob);
        assertThat(userRepository.findFollowing(bob, page(20, null)).getEdges().stream()
                .map(Edge::getNode).toList())
                .extracting(User::id).containsExactly(carol);

        // When a user has no follows or a cursor is past the final follower.
        var emptyFollowers = userRepository.findFollowers(isolated, page(20, null));
        var emptyFollowing = userRepository.findFollowing(isolated, page(20, null));
        var pastEnd = userRepository.findFollowers(bob, page(1, IdCursor.encode(carol)));

        // Then the empty pages have no cursor and no next page.
        for (var page : List.of(emptyFollowers, emptyFollowing, pastEnd)) {
            assertThat(page.getEdges().stream().map(Edge::getNode).toList()).isEmpty();
            assertThat(page.getPageInfo().getEndCursor()).isNull();
            assertThat(page.getPageInfo().isHasNextPage()).isFalse();
        }
    }

    @Test
    void rejectsInvalidPaginationArguments() {
        // Given a user whose follow list can be requested.
        Long userId = insertUser("page_user");

        // When page sizes exceed the bounds or the cursor is not positive.
        // Then invalid requests fail before querying relationships.
        for (int first : List.of(-1, 101, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> userRepository.findFollowers(userId, page(first, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> userRepository.findFollowing(userId, page(first, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> userRepository.findFollowers(userId, page(20, "MA==")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userRepository.findFollowing(userId, page(20, "LTE=")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long insertUser(String username) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.DISPLAY_NAME, username)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }

    private CursorPageRequest page(Integer first, String after) {
        return CursorPageRequest.builder()
                .first(first)
                .after(after)
                .build();
    }

}
