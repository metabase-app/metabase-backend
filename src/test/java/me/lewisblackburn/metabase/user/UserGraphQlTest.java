package me.lewisblackburn.metabase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Window;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.pagination.Subrange;
import me.lewisblackburn.metabase.config.GraphQlScalarConfiguration;
import me.lewisblackburn.metabase.config.GraphQlPaginationConfiguration;
import me.lewisblackburn.metabase.config.MethodSecurityConfiguration;
import me.lewisblackburn.metabase.security.Ownership;
import me.lewisblackburn.metabase.security.CurrentUser;
import me.lewisblackburn.metabase.security.UserPrincipal;
import me.lewisblackburn.metabase.user.model.User;
import me.lewisblackburn.metabase.pagination.JooqPagination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest({UserController.class, UserFollowController.class})
@Import({GraphQlScalarConfiguration.class, GraphQlPaginationConfiguration.class,
        MethodSecurityConfiguration.class, Ownership.class, CurrentUser.class})
class UserGraphQlTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private CursorStrategy<ScrollPosition> cursorStrategy;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserFollowService followService;

    @Test
    void followsAndUnfollowsAsTheAuthenticatedUser() {
        authenticate(1L, "alice", "ROLE_USER");
        given(followService.follow(1L, 2L)).willReturn(user(2L, "bob"));
        given(followService.unfollow(1L, 2L)).willReturn(user(2L, "bob"));

        graphQlTester.document("mutation { followUser(userId: \"2\") { id username } }")
                .execute().path("followUser.id").entity(String.class).isEqualTo("2");
        graphQlTester.document("mutation { unfollowUser(userId: \"2\") { id } }")
                .execute().path("unfollowUser.id").entity(String.class).isEqualTo("2");

        verify(followService).follow(1L, 2L);
        verify(followService).unfollow(1L, 2L);
    }

    @Test
    void deniesFollowMutationsWithoutAVerifiedIdentity() {
        for (String mutation : List.of("followUser", "unfollowUser")) {
            assertForbidden(graphQlTester.document(
                    "mutation { " + mutation + "(userId: \"2\") { id } }")
                    .execute(), mutation);
        }

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
        for (String mutation : List.of("followUser", "unfollowUser")) {
            assertForbidden(graphQlTester.document(
                    "mutation { " + mutation + "(userId: \"2\") { id } }")
                    .execute(), mutation);
        }
        org.mockito.Mockito.verifyNoInteractions(followService);
    }

    @Test
    void returnsBadRequestForInvalidFollowTargets() {
        authenticate(1L, "alice", "ROLE_USER");
        given(followService.follow(1L, 1L))
                .willThrow(new IllegalArgumentException("You cannot follow or unfollow yourself"));
        graphQlTester.document("mutation { followUser(userId: \"1\") { id } }")
                .execute().errors().satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType().toString())
                            .isEqualTo("BAD_REQUEST");
                });
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesEmailOnlyToTheAuthenticatedOwner() {
        // Given the authenticated owner has the same ID despite a changed username.
        authenticate(1L, "previous_username", "ROLE_USER");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));
        given(userRepository.find(2L)).willReturn(user(2L, "bob"));

        // When both email fields are selected.
        var response = graphQlTester.document("""
                { owner: user(id: "1") { email } other: user(id: "2") { email } }
                """).execute();

        // Then the owner's email is visible while only the other email field is forbidden.
        assertForbidden(response, "other.email");
        response.path("owner.email").entity(String.class).isEqualTo("alice@example.com");
        response.path("other.email").valueIsNull();
    }

    @Test
    void doesNotGiveAdminsAccessToAnotherUsersEmail() {
        // Given an authenticated admin is viewing a different account.
        authenticate(99L, "admin", "ROLE_ADMIN");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));

        // When the admin requests that user's email.
        var response = graphQlTester.document("{ user(id: \"1\") { email } }").execute();

        // Then ownership remains required even for administrators.
        assertForbidden(response, "user.email");
        response.path("user.email").valueIsNull();
    }

    @Test
    void hidesEmailWithoutAuthentication() {
        // Given no authenticated principal is available to the field resolver.
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));

        // When an email field is resolved directly through GraphQL.
        var response = graphQlTester.document("{ user(id: \"1\") { email } }").execute();

        // Then the field is forbidden without exposing the underlying model value.
        assertForbidden(response, "user");
        response.path("user").valueIsNull();
    }

    @Test
    void rejectsAnonymousAndUnauthenticatedPrincipalsWithMatchingNames() {
        // Given an anonymous principal has the same name as the requested account.
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", principal(1L, "alice"),
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        // When that account's email is requested.
        var anonymous = graphQlTester.document("{ user(id: \"1\") { email } }").execute();

        // Then anonymous access does not count as authenticated ownership.
        assertForbidden(anonymous, "user");
        anonymous.path("user").valueIsNull();

        // Given the matching username is present in a token that has not been authenticated.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(principal(1L, "alice"),
                        "unused"));

        // When the email is requested again, the unverified identity must also be rejected.
        var unverified = graphQlTester.document("{ user(id: \"1\") { email } }").execute();
        assertForbidden(unverified, "user");
        unverified.path("user").valueIsNull();
    }

    @Test
    void passesPaginationArgumentsAndProtectsEmailInNestedResults() {
        // Given Alice follows Bob, and Alice is the authenticated viewer.
        authenticate(1L, "alice", "ROLE_USER");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));
        String cursor = cursorStrategy.toCursor(ScrollPosition.forward(Map.of("id", 11L)));
        var following = Window.from(List.of(user(11L, "bob")),
                index -> ScrollPosition.forward(Map.of("id", 11L)), true);
        given(userRepository.findFollowing(eq(1L), any())).willReturn(following);
        given(userRepository.findFollowers(eq(1L), any())).willReturn(JooqPagination.empty());

        // When an explicit following page and the default followers page are selected.
        var response = graphQlTester.document("""
                query($after: String!) {
                  user(id: "1") {
                    following(first: 1, after: $after) {
                      edges { cursor node { id email } }
                      pageInfo { startCursor endCursor hasPreviousPage hasNextPage }
                    }
                    followers { edges { cursor node { id } } pageInfo { endCursor hasNextPage } }
                  }
                }
                """)
                .variable("after",
                        cursorStrategy.toCursor(ScrollPosition.forward(Map.of("id", 10L))))
                .execute();

        // Then pagination arguments reach the repository and nested emails remain private.
        assertForbidden(response, "user.following.edges[0].node.email");
        response.path("user.following.edges[0].node.id").entity(String.class).isEqualTo("11");
        response.path("user.following.edges[0].node.email").valueIsNull();
        response.path("user.following.edges[0].cursor").entity(String.class)
                .isEqualTo(cursor);
        response.path("user.following.pageInfo.startCursor").entity(String.class)
                .isEqualTo(cursor);
        response.path("user.following.pageInfo.hasPreviousPage").entity(Boolean.class)
                .isEqualTo(false);
        response.path("user.following.pageInfo.endCursor").entity(String.class)
                .isEqualTo(cursor);
        response.path("user.following.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true);
        response.path("user.followers.edges").entityList(User.class).hasSize(0);
        verify(userRepository).findFollowing(eq(1L),
                argThat(range -> range.count().orElse(20) == 1 && range.position().orElseThrow()
                        .equals(ScrollPosition.forward(Map.of("id", 10L)))));
        verify(userRepository).findFollowers(eq(1L),
                argThat(range -> range.count().orElse(20) == 20 && range.position().isEmpty()));
    }

    private void assertForbidden(GraphQlTester.Response response, String path) {
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("FORBIDDEN");
            assertThat(errors.getFirst().getPath()).isEqualTo(path);
        });
    }

    @Test
    void rejectsADifferentUserIdEvenWhenTheUsernameMatches() {
        // Given a different account's principal has the same username as the requested account.
        authenticate(2L, "alice", "ROLE_USER");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));

        // When email is requested, ownership is checked using the stable ID.
        var response = graphQlTester.document("{ user(id: \"1\") { email } }").execute();

        // Then a matching username does not grant access to another user's email.
        assertForbidden(response, "user.email");
        response.path("user.email").valueIsNull();
    }

    @Test
    void rejectsAuthenticatedPrincipalsWithoutAUserId() {
        // Given an authenticated principal contains only a matching username.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));

        // When email is requested without a verified user ID.
        var response = graphQlTester.document("{ user(id: \"1\") { email } }").execute();

        // Then ownership fails closed rather than falling back to username comparison.
        assertForbidden(response, "user.email");
        response.path("user.email").valueIsNull();
    }

    private UserPrincipal principal(Long id, String username) {
        return UserPrincipal.principalBuilder()
                .id(id)
                .username(username)
                .passwordHash("")
                .build();
    }

    private void authenticate(Long id, String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal(id, username), null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private User user(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .roles(List.of())
                .following(JooqPagination.empty())
                .followers(JooqPagination.empty())
                .build();
    }

}
