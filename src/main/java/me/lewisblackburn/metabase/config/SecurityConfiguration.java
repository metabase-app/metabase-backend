package me.lewisblackburn.metabase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.http.MediaType;
import org.springframework.graphql.execution.ErrorType;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CookieCsrfTokenRepository csrfTokens) {
        // Manual login must rotate an existing session ID and clear its old CSRF token.
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContexts, CookieCsrfTokenRepository csrfTokens,
            ObjectMapper objectMapper)
            throws Exception {
        return http
                .authorizeHttpRequests(
                        authorize -> authorize.requestMatchers("/health", "/actuator/health",
                                "/auth/csrf", "/graphiql", "/graphql")
                                .permitAll().anyRequest().authenticated())
                // GraphQL operations use method security; login/signup must be reachable
                // anonymously.
                .formLogin(AbstractHttpConfigurer::disable)
                // GraphiQL reads XSRF-TOKEN and sends it as the X-XSRF-TOKEN header.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(securityContexts))
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // Security filters run before GraphQL; return JSON so GraphiQL can show the error.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (request, response, exception) -> writeSecurityError(objectMapper,
                                        response, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                                objectMapper, response, HttpStatus.FORBIDDEN,
                                exception instanceof CsrfException
                                        ? "CSRF token is missing or invalid. Reload GraphiQL and try again."
                                        : "Access is denied")))
                .logout(logout -> logout.logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)))
                .build();
    }

    private void writeSecurityError(ObjectMapper objectMapper, HttpServletResponse response,
            HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorType type =
                status == HttpStatus.UNAUTHORIZED ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
        objectMapper.writeValue(response.getOutputStream(), Map.of("errors", List.of(
                Map.of("message", message, "extensions", Map.of("classification", type.name())))));
    }
}
