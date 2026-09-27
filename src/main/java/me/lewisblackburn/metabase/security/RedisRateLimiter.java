package me.lewisblackburn.metabase.security;

import java.time.Duration;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RedisRateLimiter {
    private final ProxyManager<String> buckets;

    public void check(String key, BucketConfiguration limit) {
        var bucket = buckets.builder().build(key, () -> limit);
        var result = bucket.tryConsumeAndReturnRemaining(1);
        if (!result.isConsumed()) {
            long seconds = Duration.ofNanos(result.getNanosToWaitForRefill()).toSeconds();
            throw new RateLimitExceededException(Math.max(1, seconds + 1));
        }
    }
}
