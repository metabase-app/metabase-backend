package me.lewisblackburn.metabase.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigurationTest.ProtectedController.class)
@Import({SecurityConfiguration.class, SecurityConfigurationTest.ProtectedController.class})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void rejectsAnonymousRequests() throws Exception {
        // Given a protected endpoint and no authenticated session.
        // When an anonymous client requests it.
        // Then the response is 401 rather than a login-page redirect.
        mockMvc.perform(get("/protected")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"));
        mockMvc.perform(post("/protected").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfEvenForAuthenticatedRequests() throws Exception {
        // Given an authenticated user without a CSRF token.
        // When that user submits a state-changing request.
        // Then CSRF protection rejects the request.
        mockMvc.perform(post("/protected").with(user("lewis"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].message").value(
                        "CSRF token is missing or invalid. Reload GraphiQL and try again."));
    }

    @Test
    void returnsJsonWhenGraphQlCsrfIsMissingOrStale() throws Exception {
        mockMvc.perform(post("/graphql").contentType("application/json")
                .content("{\"query\":\"{ __typename }\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
        mockMvc.perform(post("/graphql").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].message").value(
                        "CSRF token is missing or invalid. Reload GraphiQL and try again."));
    }

    @Test
    void allowsAuthenticatedRequestsWithCsrf() throws Exception {
        // Given an authenticated user with a valid CSRF token.
        // When that user submits a state-changing request.
        // Then the request reaches its controller.
        mockMvc.perform(post("/protected").with(user("lewis")).with(csrf()))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/protected")
        String read() {
            return "OK";
        }

        @PostMapping("/protected")
        String write() {
            return "OK";
        }
    }
}
