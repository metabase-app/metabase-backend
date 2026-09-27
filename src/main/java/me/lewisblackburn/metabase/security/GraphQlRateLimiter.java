package me.lewisblackburn.metabase.security;

import java.time.Duration;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GraphQlRateLimiter {
    private final RedisRateLimiter limiter;
    private final BucketConfiguration limit;

    public GraphQlRateLimiter(@Lazy ProxyManager<String> buckets,
            @Value("${metabase.graphql.rate-limit.max-calls:60}") int maxCalls,
            @Value("${metabase.graphql.rate-limit.window-seconds:60}") int windowSeconds) {
        this.limiter = new RedisRateLimiter(buckets);
        this.limit = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(maxCalls)
                        .refillIntervally(maxCalls, Duration.ofSeconds(windowSeconds)))
                .build();
    }

    public void check(String field, String clientIp) {
        limiter.check("metabase:graphql:rate-limit:" + field + ":" + clientIp, limit);
    }
}
