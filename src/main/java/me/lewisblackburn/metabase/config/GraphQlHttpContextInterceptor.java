package me.lewisblackburn.metabase.config;

import java.util.Map;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

@Component
public class GraphQlHttpContextInterceptor implements WebGraphQlInterceptor {
    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // Capture HTTP objects before GraphQL execution so auth mutations can update the session.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes http) {
            request.configureExecutionInput(
                    (input, builder) -> builder.graphQLContext(Map.of("http", http)).build());
        }
        return chain.next(request);
    }
}
