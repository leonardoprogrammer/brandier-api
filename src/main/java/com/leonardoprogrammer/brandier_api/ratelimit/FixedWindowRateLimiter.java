package com.leonardoprogrammer.brandier_api.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FixedWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);
    private static final int MAX_KEYS = 100_000;

    private record Window(Instant start, Duration length, int count) {

        Instant end() {
            return start.plus(length);
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Optional<Duration> tryConsume(String key, int limit, Duration windowLength) {
        Instant now = Instant.now(clock);

        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
            purgeExpired();
            if (windows.size() >= MAX_KEYS) {
                log.warn("Limitador de tentativas cheio ({} chaves); requisição liberada sem registro.", MAX_KEYS);
                return Optional.empty();
            }
        }

        AtomicReference<Duration> retryAfter = new AtomicReference<>();
        windows.compute(key, (k, current) -> {
            if (current == null || !now.isBefore(current.end())) {
                return new Window(now, windowLength, 1);
            }
            if (current.count() >= limit) {
                retryAfter.set(Duration.between(now, current.end()));
                return current;
            }
            return new Window(current.start(), current.length(), current.count() + 1);
        });

        return Optional.ofNullable(retryAfter.get());
    }

    public int purgeExpired() {
        Instant now = Instant.now(clock);
        int before = windows.size();
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().end()));
        return before - windows.size();
    }

    public int size() {
        return windows.size();
    }
}
