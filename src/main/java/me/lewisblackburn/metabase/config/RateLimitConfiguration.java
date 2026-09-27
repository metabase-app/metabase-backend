package me.lewisblackburn.metabase.config;

import java.time.Duration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RateLimitConfiguration {
    @Bean(destroyMethod = "close")
    @Lazy
    StatefulRedisConnection<String, byte[]> rateLimitConnection(LettuceConnectionFactory factory) {
        // Reuse Spring's configured client; Spring closes this dedicated binary connection.
        var client = (RedisClient) factory.getNativeClient();
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean(destroyMethod = "")
    @Lazy
    ProxyManager<String> rateLimitBuckets(
            StatefulRedisConnection<String, byte[]> rateLimitConnection) {
        return Bucket4jLettuce.casBasedBuilder(rateLimitConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build();
    }
}
