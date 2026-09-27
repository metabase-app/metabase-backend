package me.lewisblackburn.metabase.security;

import java.time.Duration;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private final RedisRateLimiter limiter;
    private final BucketConfiguration limit;

    public AuthRateLimiter(@Lazy ProxyManager<String> buckets,
            @Value("${metabase.auth.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${metabase.auth.rate-limit.window-seconds:60}") int windowSeconds) {
        this.limiter = new RedisRateLimiter(buckets);
        this.limit = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(maxAttempts)
                        .refillIntervally(maxAttempts, Duration.ofSeconds(windowSeconds)))
                .build();
    }

    public void check(String clientIp) {
        limiter.check("metabase:auth:rate-limit:" + clientIp, limit);
    }
}
