package dev.streamline.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void defaultsAreReasonable() {
        var policy = RetryPolicy.defaults();

        assertEquals(3, policy.maxRetries());
        assertEquals(Duration.ofMillis(100), policy.initialBackoff());
        assertEquals(Duration.ofSeconds(10), policy.maxBackoff());
        assertEquals(0.25, policy.jitterFactor(), 0.001);
        assertEquals(Duration.ofMinutes(2), policy.maxDuration());
    }

    @Test
    void delayIncreasesExponentially() {
        var policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(60))
                .jitterFactor(0.0)
                .build();

        long delay0 = policy.delayForAttempt(0).toMillis();
        long delay1 = policy.delayForAttempt(1).toMillis();
        long delay2 = policy.delayForAttempt(2).toMillis();

        assertEquals(100, delay0);
        assertEquals(200, delay1);
        assertEquals(400, delay2);
    }

    @Test
    void delayCappedAtMaxBackoff() {
        var policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(500))
                .jitterFactor(0.0)
                .build();

        // Attempt 10 would be 100 * 2^10 = 102400ms without cap
        long delay = policy.delayForAttempt(10).toMillis();

        assertEquals(500, delay);
    }

    @Test
    void shouldRetryReturnsFalseAfterMaxRetries() {
        var policy = RetryPolicy.builder().maxRetries(3).build();

        assertTrue(policy.shouldRetry(0));
        assertTrue(policy.shouldRetry(1));
        assertTrue(policy.shouldRetry(2));
        assertFalse(policy.shouldRetry(3));
        assertFalse(policy.shouldRetry(4));
    }

    @Test
    void jitterProducesDifferentValues() {
        var policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(1000))
                .maxBackoff(Duration.ofSeconds(60))
                .jitterFactor(0.25)
                .build();

        Set<Long> delays = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            delays.add(policy.delayForAttempt(0).toMillis());
        }

        // With jitter, we should see more than one distinct value across 50 calls
        assertTrue(delays.size() > 1, "Jitter should produce varying delays, got: " + delays);
    }

    @Test
    void jitterStaysWithinBounds() {
        var policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(1000))
                .maxBackoff(Duration.ofSeconds(60))
                .jitterFactor(0.25)
                .build();

        for (int i = 0; i < 100; i++) {
            long delay = policy.delayForAttempt(0).toMillis();
            // With jitterFactor=0.25: delay in [1000 * 0.75, 1000 * 1.25] = [750, 1250]
            assertTrue(delay >= 750, "Delay too low: " + delay);
            assertTrue(delay <= 1250, "Delay too high: " + delay);
        }
    }

    @Test
    void builderChaining() {
        var policy = RetryPolicy.builder()
                .maxRetries(5)
                .initialBackoff(Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(30))
                .jitterFactor(0.5)
                .maxDuration(Duration.ofMinutes(5))
                .build();

        assertEquals(5, policy.maxRetries());
        assertEquals(Duration.ofMillis(200), policy.initialBackoff());
        assertEquals(Duration.ofSeconds(30), policy.maxBackoff());
        assertEquals(0.5, policy.jitterFactor(), 0.001);
        assertEquals(Duration.ofMinutes(5), policy.maxDuration());
    }

    @Test
    void zeroRetriesMeansNoRetry() {
        var policy = RetryPolicy.builder().maxRetries(0).build();

        assertFalse(policy.shouldRetry(0));
    }

    @Test
    void highAttemptDoesNotOverflow() {
        var policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(10))
                .jitterFactor(0.0)
                .build();

        // Attempt 50 would overflow without the Math.min(attempt, 30) guard
        long delay = policy.delayForAttempt(50).toMillis();
        assertEquals(10_000, delay);
    }
}
