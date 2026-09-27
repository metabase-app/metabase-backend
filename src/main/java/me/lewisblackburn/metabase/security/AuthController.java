package me.lewisblackburn.metabase.security;

import jakarta.validation.Valid;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final SignupService signupService;
    private final LoginService loginService;
    private final AuthRateLimiter rateLimiter;

    @MutationMapping
    public AuthPayload signup(@Argument @Valid SignupRequest input,
            @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check(http.getRequest().getRemoteAddr());
        // Create and commit the account before saving its authenticated session.
        signupService.signup(input);
        var credentials = LoginRequest.builder()
                .username(input.username())
                .password(input.password())
                .build();
        return loginService.login(credentials, http.getRequest(), http.getResponse());
    }

    @MutationMapping
    public AuthPayload login(@Argument @Valid LoginRequest input,
            @ContextValue("http") ServletRequestAttributes http) {
        rateLimiter.check(http.getRequest().getRemoteAddr());
        return loginService.login(input, http.getRequest(), http.getResponse());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean logout(@ContextValue("http") ServletRequestAttributes http)
            throws ServletException {
        // Spring's servlet integration runs its configured session and CSRF logout handlers.
        http.getRequest().logout();
        return true;
    }
}
