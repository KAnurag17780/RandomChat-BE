package dev.randomchat.platform.chat;

import dev.randomchat.platform.chat.handler.SessionRateLimiter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the per-session rate limiter.
 */
@Tag("unit")
class SessionRateLimiterTest {

    @Test
    void withinLimit_allRequestsAllowed() {
        SessionRateLimiter limiter = new SessionRateLimiter(5, 10_000);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire()).as("request %d should be allowed", i + 1).isTrue();
        }
    }

    @Test
    void exceedingLimit_requestRejected() {
        SessionRateLimiter limiter = new SessionRateLimiter(3, 10_000);

        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        // 4th request exceeds limit
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void afterWindowExpires_counterResets() throws InterruptedException {
        // Use a very short window (50ms) to test reset behaviour
        SessionRateLimiter limiter = new SessionRateLimiter(2, 50);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse(); // exceeded

        Thread.sleep(60); // wait for window to expire

        // New window: should be allowed again
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void limitOfOne_secondRequestRejected() {
        SessionRateLimiter limiter = new SessionRateLimiter(1, 10_000);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void currentCount_reflectsRequests() {
        SessionRateLimiter limiter = new SessionRateLimiter(10, 10_000);

        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.currentCount()).isEqualTo(3);
    }
}
