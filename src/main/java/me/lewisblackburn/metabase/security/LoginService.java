package me.lewisblackburn.metabase.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContexts;

    public AuthPayload login(LoginRequest input, HttpServletRequest request,
            HttpServletResponse response) {
        var token = UsernamePasswordAuthenticationToken.unauthenticated(input.username(),
                input.password());
        var authentication = authenticationManager.authenticate(token);
        sessionStrategy.onAuthentication(authentication, request, response);

        // GraphQL login runs outside Spring's login filter, so persist the session explicitly.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);

        var principal = (UserPrincipal) authentication.getPrincipal();
        return AuthPayload.builder().id(principal.id()).username(principal.getUsername()).build();
    }
}
