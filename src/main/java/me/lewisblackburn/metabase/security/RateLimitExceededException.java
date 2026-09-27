package me.lewisblackburn.metabase.security;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many authentication attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
