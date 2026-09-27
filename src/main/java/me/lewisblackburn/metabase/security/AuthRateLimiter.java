package me.lewisblackburn.metabase.security;

import java.time.Duration;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private final ProxyManager<String> buckets;
    private final BucketConfiguration limit;

    public AuthRateLimiter(@Lazy ProxyManager<String> buckets,
            @Value("${metabase.auth.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${metabase.auth.rate-limit.window-seconds:60}") int windowSeconds) {
        this.buckets = buckets;
        this.limit = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(maxAttempts)
                        .refillIntervally(maxAttempts, Duration.ofSeconds(windowSeconds)))
                .build();
    }

    public void check(String clientIp) {
        var bucket = buckets.builder().build("metabase:auth:rate-limit:" + clientIp, () -> limit);
        var result = bucket.tryConsumeAndReturnRemaining(1);
        if (!result.isConsumed()) {
            long seconds = Duration.ofNanos(result.getNanosToWaitForRefill()).toSeconds();
            throw new RateLimitExceededException(Math.max(1, seconds + 1));
        }
    }
}
