package dev.randomchat.platform.chat.handler;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple sliding-window rate limiter for a single WebSocket session.
 * <p>
 * Allows up to {@code maxRequests} events per {@code windowMillis} rolling window.
 * Thread-safe using atomic operations only — no locks needed for per-session use.
 */
public class SessionRateLimiter {

    private final int maxRequests;
    private final long windowMillis;

    /** Timestamp (epoch millis) of when the current window started. */
    private final AtomicLong windowStart = new AtomicLong(Instant.now().toEpochMilli());

    /** Count of events in the current window. */
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * @param maxRequests maximum number of allowed events per window
     * @param windowMillis duration of the rolling window in milliseconds
     */
    public SessionRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    /**
     * Records an event and returns whether it is allowed.
     *
     * @return {@code true} if the event is within the rate limit,
     *         {@code false} if it should be rejected.
     */
    public boolean tryAcquire() {
        long now = Instant.now().toEpochMilli();
        long start = windowStart.get();

        if (now - start >= windowMillis) {
            // Window expired — reset atomically using CAS to avoid double-reset races
            if (windowStart.compareAndSet(start, now)) {
                count.set(1);
                return true;
            }
            // Another thread already reset — fall through and count normally
        }

        return count.incrementAndGet() <= maxRequests;
    }

    /**
     * Returns the current event count in the active window (for diagnostics).
     */
    public int currentCount() {
        return count.get();
    }
}
