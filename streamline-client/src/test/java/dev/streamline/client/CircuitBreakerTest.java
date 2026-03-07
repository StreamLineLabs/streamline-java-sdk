package dev.streamline.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker.Config quickConfig(int failureThreshold, int successThreshold, long openTimeoutMs) {
        return CircuitBreaker.Config.builder()
                .failureThreshold(failureThreshold)
                .successThreshold(successThreshold)
                .openTimeout(Duration.ofMillis(openTimeoutMs))
                .halfOpenMaxRequests(2)
                .build();
    }

    @Test
    void startsClosed() {
        var cb = CircuitBreaker.withDefaults();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allow());
    }

    @Test
    void opensAfterFailureThreshold() {
        var cb = new CircuitBreaker(quickConfig(3, 1, 10_000));

        for (int i = 0; i < 3; i++) {
            cb.allow();
            cb.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allow(), "should reject requests when open");
    }

    @Test
    void doesNotOpenBelowThreshold() {
        var cb = new CircuitBreaker(quickConfig(3, 1, 10_000));

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allow());
    }

    @Test
    void transitionsToHalfOpenAfterTimeout() throws Exception {
        var cb = new CircuitBreaker(quickConfig(2, 1, 50));

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        Thread.sleep(60);

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
        assertTrue(cb.allow());
    }

    @Test
    void closesOnHalfOpenSuccess() throws Exception {
        var cb = new CircuitBreaker(quickConfig(2, 1, 50));

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();

        Thread.sleep(60);

        cb.allow();
        cb.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void reopensOnHalfOpenFailure() throws Exception {
        var cb = new CircuitBreaker(quickConfig(2, 2, 50));

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();

        Thread.sleep(60);

        cb.allow();
        cb.recordFailure();

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void halfOpenLimitsProbeRequests() throws Exception {
        var config = CircuitBreaker.Config.builder()
                .failureThreshold(1)
                .successThreshold(1)
                .openTimeout(Duration.ofMillis(50))
                .halfOpenMaxRequests(2)
                .build();
        var cb = new CircuitBreaker(config);

        cb.allow();
        cb.recordFailure();

        Thread.sleep(60);

        assertTrue(cb.allow(), "first probe should be allowed");
        assertTrue(cb.allow(), "second probe should be allowed");
        assertFalse(cb.allow(), "third probe should be rejected");
    }

    @Test
    void successResetsFailureCount() {
        var cb = new CircuitBreaker(quickConfig(3, 1, 10_000));

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordSuccess(); // resets count

        cb.allow();
        cb.recordFailure();
        cb.allow();
        cb.recordFailure();

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
                "should still be closed because success reset the failure count");
    }

    @Test
    void resetReturnsToClosed() {
        var cb = new CircuitBreaker(quickConfig(1, 1, 600_000));

        cb.allow();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        cb.reset();

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allow());
    }

    @Test
    void stateChangeCallbackFires() throws Exception {
        List<String> transitions = new ArrayList<>();

        var config = CircuitBreaker.Config.builder()
                .failureThreshold(1)
                .successThreshold(1)
                .openTimeout(Duration.ofMillis(50))
                .halfOpenMaxRequests(1)
                .onStateChange((from, to) -> transitions.add(from + "->" + to))
                .build();
        var cb = new CircuitBreaker(config);

        cb.allow();
        cb.recordFailure(); // CLOSED -> OPEN

        Thread.sleep(60);
        cb.allow(); // OPEN -> HALF_OPEN
        cb.recordSuccess(); // HALF_OPEN -> CLOSED

        assertEquals(3, transitions.size());
        assertEquals("CLOSED->OPEN", transitions.get(0));
        assertEquals("OPEN->HALF_OPEN", transitions.get(1));
        assertEquals("HALF_OPEN->CLOSED", transitions.get(2));
    }

    @Test
    void executeReturnsResultOnSuccess() {
        var cb = CircuitBreaker.withDefaults();

        String result = cb.execute(() -> "hello");

        assertEquals("hello", result);
    }

    @Test
    void executeThrowsWhenCircuitOpen() {
        var cb = new CircuitBreaker(quickConfig(1, 1, 600_000));

        cb.allow();
        cb.recordFailure();

        var ex = assertThrows(StreamlineException.class, () -> cb.execute(() -> "should not reach"));
        assertTrue(ex.getMessage().contains("Circuit breaker is open"));
        assertTrue(ex.isRetryable());
    }

    @Test
    void executeRecordsFailureOnRetryableError() {
        var cb = new CircuitBreaker(quickConfig(2, 1, 600_000));

        assertThrows(StreamlineException.class, () ->
                cb.execute(() -> { throw new StreamlineException("fail", true); }));
        assertThrows(StreamlineException.class, () ->
                cb.execute(() -> { throw new StreamlineException("fail", true); }));

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void executeDoesNotTripOnNonRetryableError() {
        var cb = new CircuitBreaker(quickConfig(2, 1, 600_000));

        for (int i = 0; i < 5; i++) {
            assertThrows(StreamlineException.class, () ->
                    cb.execute(() -> { throw new StreamlineException("auth failed", null, "AUTH_FAILED", null); }));
        }

        // Non-retryable errors don't count as failures for circuit breaker
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }
}
