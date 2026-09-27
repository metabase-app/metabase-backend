package me.lewisblackburn.metabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import me.lewisblackburn.metabase.security.AuthRateLimiter;
import me.lewisblackburn.metabase.security.RateLimitExceededException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@Testcontainers
class RedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private ProxyManager<String> buckets;

    @Test
    void allowsAttemptsAgainAfterTheRefillInterval() {
        var shortLimit = new AuthRateLimiter(buckets, 1, 1);
        shortLimit.check("refill-test");
        assertThatThrownBy(() -> shortLimit.check("refill-test"))
                .isInstanceOf(RateLimitExceededException.class);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3))
                .ignoreException(RateLimitExceededException.class)
                .untilAsserted(() -> shortLimit.check("refill-test"));
    }

    @Test
    void rateLimitExpiresWithoutExtendingOnDeniedAttempts() {
        String ip = "expiry-test";
        String key = "metabase:auth:rate-limit:" + ip;
        redisTemplate.delete(key);
        for (int i = 0; i < 10; i++) {
            rateLimiter.check(ip);
        }
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 70L);
        redisTemplate.expire(key, Duration.ofSeconds(20));
        assertThatThrownBy(() -> rateLimiter.check(ip))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 20L);
        redisTemplate.expire(key, Duration.ZERO);
        rateLimiter.check(ip);
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 70L);
        rateLimiter.check("another-client");
    }

    @Test
    void sharesAnAtomicLimitAcrossConcurrentCallers() {
        String ip = "concurrency-test";
        redisTemplate.delete("metabase:auth:rate-limit:" + ip);
        var attempts = IntStream.range(0, 30).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.check(ip);
                return true;
            } catch (RateLimitExceededException exception) {
                return false;
            }
        })).toList();
        long allowed = attempts.stream().map(CompletableFuture::join).filter(Boolean::booleanValue)
                .count();
        assertThat(allowed).isEqualTo(10);
    }

    @Test
    void redisWorks() {
        // Given a value has been stored in Redis.
        redisTemplate.opsForValue().set("test", "hello");

        // When the value is retrieved using the same key.
        String result = redisTemplate.opsForValue().get("test");

        // Then Redis returns the stored value.
        assertThat(result).isEqualTo("hello");
    }
}
