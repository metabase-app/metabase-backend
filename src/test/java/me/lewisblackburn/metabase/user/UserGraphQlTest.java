package me.lewisblackburn.metabase.user;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;
import me.lewisblackburn.metabase.security.GraphQlRateLimiter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import me.lewisblackburn.metabase.event.UserAuditController;
import me.lewisblackburn.metabase.event.UserAuditEvent;
import me.lewisblackburn.metabase.event.UserAuditRepository;
import java.util.Map;
import me.lewisblackburn.metabase.config.GraphQlScalarConfiguration;
import me.lewisblackburn.metabase.config.MethodSecurityConfiguration;
import me.lewisblackburn.metabase.security.Ownership;
import me.lewisblackburn.metabase.security.CurrentUser;
import me.lewisblackburn.metabase.security.UserPrincipal;
import me.lewisblackburn.metabase.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest({UserController.class, UserFollowController.class, UserAuditController.class})
@Import({GraphQlScalarConfiguration.class, MethodSecurityConfiguration.class,
        Ownership.class, CurrentUser.class, UserLookup.class})
class UserGraphQlTest {

    @MockitoBean
    private GraphQlRateLimiter rateLimiter;

    @BeforeEach
    void provideHttpContext() {
        var http = new ServletRequestAttributes(
                new MockHttpServletRequest());
        graphQlTester =
                ((ExecutionGraphQlServiceTester) graphQlTester).mutate()
                        .configureExecutionInput((input, builder) -> builder
                                .graphQLContext(context -> context.put("http", http)).build())
                        .build();
    }

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserFollowService followService;

    @MockitoBean
    private UserAuditRepository auditRepository;

    @Test
    void returnsMyProfileUsingTheAuthenticatedId() {
        authenticate(1L, "old_username", "ROLE_USER");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));
        given(userRepository.findRolesByUserIds(List.of(1L)))
                .willReturn(Map.of(1L, List.of(me.lewisblackburn.metabase.user.model.Role.USER)));
        given(userRepository.findFollowers(1L, 0, 1)).willReturn(List.of());
        given(userRepository.findFollowing(1L, 0, 1)).willReturn(List.of());

        var response = graphQlTester.document("""
                { me {
                  id username email roles
                  followers(limit: 1) { id }
                  following(limit: 1) { id }
                } }
                """).execute();

        response.path("me.id").entity(String.class).isEqualTo("1");
        response.path("me.username").entity(String.class).isEqualTo("alice");
        response.path("me.email").entity(String.class).isEqualTo("alice@example.com");
        response.path("me.roles").entityList(String.class).containsExactly("USER");
        response.path("me.followers").entityList(Object.class).hasSize(0);
        response.path("me.following").entityList(Object.class).hasSize(0);
        verify(userRepository).find(1L);
    }

    @Test
    void deniesMeWithoutAVerifiedIdentity() {
        assertForbidden(graphQlTester.document("{ me { id } }").execute(), "me");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
        assertForbidden(graphQlTester.document("{ me { id } }").execute(), "me");
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsMeForAMissingOrDeletedAccount() {
        authenticate(1L, "alice", "ROLE_USER");
        var missing = graphQlTester.document("{ me { id } }").execute();
        missing.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("BAD_REQUEST");
        });

        given(userRepository.find(1L)).willReturn(User.builder()
                .id(1L).username("alice").deletedAt(java.time.OffsetDateTime.now()).build());
        graphQlTester.document("{ me { id } }").execute().errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getErrorType().toString()).isEqualTo("BAD_REQUEST");
        });
    }

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

    @Test
    void returnsPaginatedUserLogsThroughBothFieldsForTheOwner() {
        // Given Alice owns one audit event and is the authenticated viewer.
        authenticate(1L, "alice", "ROLE_USER");
        given(userRepository.find(1L)).willReturn(user(1L, "alice"));
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OffsetDateTime at = OffsetDateTime.parse("2026-01-02T12:00:00Z");
        var event = UserAuditEvent.builder()
                .id(eventId)
                .operationId(eventId)
                .occurredAt(at)
                .action("USER_FOLLOWED")
                .actorUserId(1L)
                .outcome("SUCCESS")
                .details("{\"followedUserId\":2}")
                .build();
        given(auditRepository.findByActor(1L, 0, 1)).willReturn(List.of(event));

        // When the top-level query and computed User field request their own lists.
        var response = graphQlTester.document("""
                { userLogs(limit: 1, userId: "1") {
                    id action actorUserId details
                  }
                  user(id: "1") { logs(limit: 1) {
                    id action
                  } }
                }
                """).execute();

        // Then both fields expose the same event.
        response.path("userLogs[0].id").entity(String.class)
                .isEqualTo(eventId.toString());
        response.path("userLogs[0].action").entity(String.class)
                .isEqualTo("USER_FOLLOWED");
        response.path("userLogs[0].actorUserId").entity(String.class)
                .isEqualTo("1");
        response.path("user.logs[0].id").entity(String.class)
                .isEqualTo(eventId.toString());

        // When the next page uses offset one, then the repository receives that offset.
        graphQlTester.document("""
                { userLogs(userId: "1", offset: 1, limit: 1) { id } }
                """).execute().path("userLogs").entityList(Object.class).hasSize(0);
        verify(auditRepository).findByActor(1L, 1, 1);
        verify(auditRepository, org.mockito.Mockito.times(2)).findByActor(1L, 0, 1);
    }

    @Test
    void deniesAnotherUsersLogsWithoutReadingAuditRows() {
        // Given Alice is signed in and Bob has a user profile.
        authenticate(1L, "alice", "ROLE_USER");
        given(userRepository.find(2L)).willReturn(user(2L, "bob"));

        // When Alice requests Bob's logs through either field.
        var response = graphQlTester.document("""
                { userLogs(userId: "2") { id }
                  user(id: "2") { logs { id } }
                }
                """).execute();

        // Then both fields are forbidden before audit storage is queried.
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(2);
            assertThat(errors).allSatisfy(error -> assertThat(error.getErrorType().toString())
                    .isEqualTo("FORBIDDEN"));
        });
        org.mockito.Mockito.verifyNoInteractions(auditRepository);
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
        var following = List.of(user(11L, "bob"));
        given(userRepository.findFollowing(1L, 2, 1)).willReturn(following);
        given(userRepository.findFollowers(1L, 0, 20)).willReturn(List.of());

        // When an explicit following page and the default followers page are selected.
        var response = graphQlTester.document("""
                {
                  user(id: "1") {
                    following(offset: 2, limit: 1) { id email }
                    followers { id }
                  }
                }
                """).execute();

        // Then pagination arguments reach the repository and nested emails remain private.
        assertForbidden(response, "user.following[0].email");
        response.path("user.following[0].id").entity(String.class).isEqualTo("11");
        response.path("user.following[0].email").valueIsNull();
        response.path("user.followers").entityList(Object.class).hasSize(0);
        verify(userRepository).findFollowing(1L, 2, 1);
        verify(userRepository).findFollowers(1L, 0, 20);
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
                .following(List.of())
                .followers(List.of())
                .build();
    }

}
