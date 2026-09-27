package me.lewisblackburn.metabase.security;

import static me.lewisblackburn.metabase.jooq.tables.Users.USERS;
import static me.lewisblackburn.metabase.jooq.tables.UserRoles.USER_ROLES;
import static me.lewisblackburn.metabase.jooq.tables.UserFollows.USER_FOLLOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {
    private static final String PASSWORD = "test-password-123";
    private static final String SIGNUP =
            "mutation($input: SignupInput!) { signup(input: $input) { id username } }";
    private static final String LOGIN =
            "mutation($input: LoginInput!) { login(input: $input) { id username } }";

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
    private MockMvc mockMvc;
    @Autowired
    private DSLContext dsl;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void removeTestAccounts() {
        dsl.deleteFrom(USERS).where(USERS.USERNAME.startsWith("auth_")).execute();
    }

    @Test
    void explainsTheShortPasswordWithoutEchoingItsValue() throws Exception {
        signup("auth_short", "short@example.com", "lewis", csrfCookie(null))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].message",
                        org.hamcrest.Matchers.containsString("password")))
                .andExpect(jsonPath("$.errors[0].message",
                        org.hamcrest.Matchers.containsString("12")));
    }

    @Test
    void signupImmediatelyAuthenticatesTheNewAccount() throws Exception {
        var result = signup("auth_new", "new@example.com", PASSWORD, csrfCookie(null))
                .andExpect(jsonPath("$.errors").doesNotExist()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        Long userId = dsl.select(USERS.ID).from(USERS)
                .where(USERS.USERNAME.eq("auth_new")).fetchSingle(USERS.ID);
        graphql("{ me { id email } }", Map.of(), csrfCookie(session),
                session)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.me.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.me.email").value("new@example.com"));
    }

    @Test
    void signsUpLogsInAndUsesTheSessionForGraphQlAndLogout() throws Exception {
        Cookie csrf = csrfCookie(null);
        signup("auth_alice", "ALICE@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors").doesNotExist());
        signup("auth_bob", "bob@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors").doesNotExist());
        var alice = dsl.selectFrom(USERS).where(USERS.USERNAME.eq("auth_alice")).fetchSingle();
        Long bobId = dsl.select(USERS.ID).from(USERS)
                .where(USERS.USERNAME.eq("auth_bob")).fetchSingle(USERS.ID);

        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(alice.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, alice.getPasswordHash())).isTrue();
        assertThat(dsl.select(USER_ROLES.ROLE).from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(alice.getId())).fetch(USER_ROLES.ROLE))
                .containsExactly("USER");

        MockHttpSession beforeLogin = new MockHttpSession();
        String oldSessionId = beforeLogin.getId();
        MvcResult login = graphql(LOGIN,
                Map.of("input", Map.of("username", "AUTH_ALICE", "password", PASSWORD)),
                csrf, beforeLogin)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.login.id").value(alice.getId().toString()))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(oldSessionId);

        Cookie freshCsrf = csrfCookie(session);
        assertThat(freshCsrf.getValue()).isNotEqualTo(csrf.getValue());
        mockMvc.perform(get("/graphiql").param("path", "/graphql").session(session))
                .andExpect(status().isOk());

        String query = "{ me { id email } }";
        mockMvc.perform(post("/graphql").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", query))))
                .andExpect(status().isForbidden());
        graphql(query, Map.of(), freshCsrf, session)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.me.id").value(alice.getId().toString()))
                .andExpect(jsonPath("$.data.me.email").value("alice@example.com"));

        String mutation = "mutation { followUser(userId: \"" + bobId + "\") { id } }";
        graphql(mutation, Map.of(), freshCsrf, session)
                .andExpect(jsonPath("$.errors").doesNotExist());
        assertThat(dsl.fetchExists(USER_FOLLOWS, USER_FOLLOWS.FOLLOWER_ID.eq(alice.getId())
                .and(USER_FOLLOWS.FOLLOWED_ID.eq(bobId)))).isTrue();

        graphql("mutation { logout }", Map.of(), freshCsrf, session)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.logout").value(true));
        assertThat(session.isInvalid()).isTrue();
        graphql(query, Map.of(), csrfCookie(null), null)
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    @Test
    void rejectsDuplicateUsernamesAndNormalisedEmails() throws Exception {
        Cookie csrf = csrfCookie(null);
        signup("auth_alice", "alice@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors").doesNotExist());
        signup("AUTH_ALICE", "other@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
        signup("auth_other", "ALICE@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
        assertThat(dsl.fetchCount(USERS, USERS.USERNAME.startsWith("auth_"))).isEqualTo(1);
    }

    @Test
    void rejectsInvalidSignupInputAndMissingCsrf() throws Exception {
        Cookie csrf = csrfCookie(null);
        for (var input : new String[][] {
                {"x", "alice@example.com", PASSWORD},
                {"auth_alice", "invalid-email", PASSWORD},
                {"auth_alice", "alice@example.com", "short"},
                {"auth_alice", "alice@example.com", "é".repeat(40)}}) {
            signup(input[0], input[1], input[2], csrf)
                    .andExpect(
                            jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
        }
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", SIGNUP))))
                .andExpect(status().isForbidden());
        assertThat(dsl.fetchCount(USERS, USERS.USERNAME.startsWith("auth_"))).isZero();
    }

    @Test
    void rejectsInvalidLoginWithoutCreatingAnAuthenticatedSession() throws Exception {
        Cookie csrf = csrfCookie(null);
        signup("auth_alice", "alice@example.com", PASSWORD, csrf)
                .andExpect(jsonPath("$.errors").doesNotExist());
        for (String username : new String[] {"auth_alice", "auth_unknown"}) {
            var result = graphql(LOGIN,
                    Map.of("input", Map.of("username", username, "password", "wrong-password")),
                    csrf, null)
                    .andExpect(
                            jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.errors[0].message").value("Invalid credentials"))
                    .andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
    }

    @Test
    void keepsExistingOperationsProtectedWhenTheGraphQlEndpointIsPublic() throws Exception {
        Cookie csrf = csrfCookie(null);
        mockMvc.perform(get("/graphiql").param("path", "/graphql")).andExpect(status().isOk());
        for (String operation : new String[] {
                "{ me { id } }", "{ users { id } }", "{ user(id: \"1\") { id } }",
                "{ movies { id } }", "{ movie(id: \"1\") { id } }",
                "mutation { importMovie(provider: TMDB, externalId: \"603\") { id } }",
                "mutation { followUser(userId: \"1\") { id } }",
                "mutation { unfollowUser(userId: \"1\") { id } }", "mutation { logout }"}) {
            graphql(operation, Map.of(), csrf, null)
                    .andExpect(
                            jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
        }
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/signup").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    private Cookie csrfCookie(MockHttpSession session) throws Exception {
        var request = get("/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        Cookie cookie = mockMvc.perform(request).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private ResultActions signup(String username, String email, String password, Cookie csrf)
            throws Exception {
        var result = graphql(SIGNUP,
                Map.of("input", Map.of("username", username, "email", email, "password", password)),
                csrf, null);
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain(password, "passwordHash");
        return result;
    }

    private ResultActions graphql(String query, Map<String, ?> variables, Cookie csrf,
            MockHttpSession session) throws Exception {
        var request = post("/graphql").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper
                        .writeValueAsString(Map.of("query", query, "variables", variables)));
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }
}
