package me.lewisblackburn.metabase.security;

import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class DatabaseUserDetailsServiceIntegrationTest {
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
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Ownership ownership;

    @Autowired
    private DSLContext dsl;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loadsStableIdentityPasswordHashAndAuthoritiesCaseInsensitively() {
        // Given a stored account has an encoded password and both supported roles.
        String hash = passwordEncoder.encode("test-password");
        Long userId = insertUser("alice", hash, false);
        dsl.insertInto(USER_ROLES, USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .values(userId, "USER")
                .values(userId, "ADMIN")
                .execute();

        // When credentials are loaded using a differently cased login username.
        UserPrincipal principal = userDetailsService.loadUserByUsername("ALICE");

        // Then the stable ID, canonical username, encoded password and Spring authorities are
        // retained.
        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.getName()).isEqualTo("alice");
        assertThat(principal.getPassword()).isEqualTo(hash);
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void rejectsMissingDeletedAndCredentiallessAccountsWithTheSameMessage() {
        // Given one account has been deleted and another has no login password.
        insertUser("deleted_user", passwordEncoder.encode("test-password"), true);
        insertUser("no_password", null, false);

        // When these accounts or an unknown account are looked up for login.
        // Then the service gives the same generic failure without distinguishing account state.
        for (String username : new String[] {"deleted_user", "no_password", "unknown", "", null}) {
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(username))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Invalid credentials");
        }
    }

    @Test
    void verifiesPasswordsAndErasesCredentialsWhilePreservingOwnership() {
        // Given a stored account and Spring's standard username/password provider.
        Long userId = insertUser("alice", passwordEncoder.encode("test-password"), false);
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        var manager = new ProviderManager(provider);

        // When the correct password is authenticated.
        Authentication authentication = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "test-password"));

        // Then sensitive credentials are erased and the ID-based ownership check still works.
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.getPassword()).isNull();
        assertThat(authentication.getCredentials()).isNull();
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertThat(ownership.isOwner(userId)).isTrue();
        assertThat(ownership.isOwner(userId + 1)).isFalse();

        // When login is attempted again, the stored hash remains available and wrong passwords
        // fail.
        assertThat(userDetailsService.loadUserByUsername("alice").getPassword()).isNotNull();
        assertThatThrownBy(() -> manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private Long insertUser(String username, String hash, boolean deleted) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.DISPLAY_NAME, username)
                .set(USERS.PASSWORD_HASH, hash)
                .set(USERS.DELETED_AT, deleted ? OffsetDateTime.now() : null)
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
    }
}
