package me.lewisblackburn.metabase.config;

import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.github.bucket4j.BucketConfiguration;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.security.RateLimitExceededException;
import me.lewisblackburn.metabase.security.RedisRateLimiter;
import org.springframework.web.context.request.ServletRequestAttributes;

@RequiredArgsConstructor
public class GraphQlRateLimitInstrumentation extends SimplePerformantInstrumentation {
    private final RedisRateLimiter limiter;
    private final BucketConfiguration limit;
    private final GraphQlExceptionHandlerAdvice errors;

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> fetcher,
            InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        var environment = parameters.getEnvironment();
        String field = environment.getFieldDefinition().getName();
        // Only root fields; login/signup already have their own shared limit.
        if (environment.getExecutionStepInfo().getPath().getLevel() != 1
                || field.startsWith("__") || field.equals("login") || field.equals("signup")) {
            return fetcher;
        }
        return input -> {
            ServletRequestAttributes http = input.getGraphQlContext().get("http");
            if (http != null) {
                // Use the schema field name, so aliases share the same allowance.
                String key = "metabase:graphql:rate-limit:" + field + ":"
                        + http.getRequest().getRemoteAddr();
                try {
                    limiter.check(key, limit);
                } catch (RateLimitExceededException exception) {
                    return DataFetcherResult.newResult()
                            .error(errors.handleRateLimit(exception, input)).build();
                }
            }
            return fetcher.get(input);
        };
    }
}
