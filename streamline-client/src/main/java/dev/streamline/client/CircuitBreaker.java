package dev.streamline.client;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Circuit breaker pattern for resilient Streamline operations.
 *
 * <p>Tracks failures and temporarily stops requests to a failing service,
 * allowing it time to recover before retrying. Models the standard
 * Closed → Open → Half-Open state machine.
 *
 * <p>Only retryable errors (where {@link StreamlineException#isRetryable()} is
 * true) should trip the circuit breaker. Non-retryable errors pass through
 * without affecting the circuit state.
 *
 * <p>Example usage:
 * <pre>{@code
 * CircuitBreaker breaker = CircuitBreaker.withDefaults();
 *
 * RecordMetadata result = breaker.execute(() -> producer.send(topic, key, value).get());
 * }</pre>
 */
public class CircuitBreaker {

    /** Circuit breaker states. */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** Configuration for the circuit breaker. */
    public static class Config {
        private final int failureThreshold;
        private final int successThreshold;
        private final Duration openTimeout;
        private final int halfOpenMaxRequests;
        private final BiConsumer<State, State> onStateChange;

        private Config(Builder builder) {
            this.failureThreshold = builder.failureThreshold;
            this.successThreshold = builder.successThreshold;
            this.openTimeout = builder.openTimeout;
            this.halfOpenMaxRequests = builder.halfOpenMaxRequests;
            this.onStateChange = builder.onStateChange;
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int failureThreshold = 5;
            private int successThreshold = 2;
            private Duration openTimeout = Duration.ofSeconds(30);
            private int halfOpenMaxRequests = 3;
            private BiConsumer<State, State> onStateChange;

            public Builder failureThreshold(int value) { this.failureThreshold = value; return this; }
            public Builder successThreshold(int value) { this.successThreshold = value; return this; }
            public Builder openTimeout(Duration value) { this.openTimeout = value; return this; }
            public Builder halfOpenMaxRequests(int value) { this.halfOpenMaxRequests = value; return this; }
            public Builder onStateChange(BiConsumer<State, State> listener) { this.onStateChange = listener; return this; }
            public Config build() { return new Config(this); }
        }
    }

    private final Config config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger halfOpenCount = new AtomicInteger();
    private volatile Instant lastFailureAt = Instant.EPOCH;

    public CircuitBreaker(Config config) {
        this.config = config;
    }

    /** Creates a circuit breaker with sensible defaults. */
    public static CircuitBreaker withDefaults() {
        return new CircuitBreaker(Config.builder().build());
    }

    /** Returns the current circuit state. */
    public State getState() {
        checkOpenTimeout();
        return state.get();
    }

    /** Returns true if a request should be allowed through. */
    public boolean allow() {
        switch (state.get()) {
            case CLOSED:
                return true;
            case OPEN:
                if (Duration.between(lastFailureAt, Instant.now()).compareTo(config.openTimeout) >= 0) {
                    transition(State.HALF_OPEN);
                    halfOpenCount.set(1);
                    return true;
                }
                return false;
            case HALF_OPEN:
                return halfOpenCount.incrementAndGet() <= config.halfOpenMaxRequests;
            default:
                return true;
        }
    }

    /** Record a successful operation. */
    public void recordSuccess() {
        switch (state.get()) {
            case CLOSED:
                failureCount.set(0);
                break;
            case HALF_OPEN:
                if (successCount.incrementAndGet() >= config.successThreshold) {
                    transition(State.CLOSED);
                }
                break;
        }
    }

    /** Record a failed operation. Only retryable failures should be recorded. */
    public void recordFailure() {
        lastFailureAt = Instant.now();
        switch (state.get()) {
            case CLOSED:
                if (failureCount.incrementAndGet() >= config.failureThreshold) {
                    transition(State.OPEN);
                }
                break;
            case HALF_OPEN:
                transition(State.OPEN);
                break;
        }
    }

    /** Reset the circuit breaker to closed state. */
    public void reset() {
        transition(State.CLOSED);
    }

    /**
     * Execute a supplier through the circuit breaker.
     *
     * @throws StreamlineException with hint about circuit breaker if the circuit is open
     */
    public <T> T execute(Supplier<T> action) {
        if (!allow()) {
            throw new StreamlineException(
                "Circuit breaker is open — too many recent failures",
                null,
                true,
                "The client detected repeated failures and is temporarily pausing requests."
            );
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (StreamlineException e) {
            if (e.isRetryable()) {
                recordFailure();
            }
            throw e;
        } catch (Exception e) {
            recordFailure();
            throw new StreamlineException("Operation failed", e);
        }
    }

    private void checkOpenTimeout() {
        if (state.get() == State.OPEN
            && Duration.between(lastFailureAt, Instant.now()).compareTo(config.openTimeout) >= 0) {
            transition(State.HALF_OPEN);
        }
    }

    private void transition(State to) {
        State from = state.getAndSet(to);
        if (from == to) return;

        failureCount.set(0);
        successCount.set(0);
        halfOpenCount.set(0);

        if (config.onStateChange != null) {
            config.onStateChange.accept(from, to);
        }
    }
}
