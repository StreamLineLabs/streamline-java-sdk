package dev.streamline.client;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configurable retry policy with exponential backoff and jitter.
 *
 * <p>Calculates per-attempt delays using exponential backoff capped at a
 * configurable maximum, then applies random jitter to prevent thundering-herd
 * effects across many clients retrying simultaneously.
 *
 * <p>Example usage:
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *     .maxRetries(5)
 *     .initialBackoff(Duration.ofMillis(200))
 *     .maxBackoff(Duration.ofSeconds(30))
 *     .jitterFactor(0.25)
 *     .build();
 *
 * for (int attempt = 0; policy.shouldRetry(attempt); attempt++) {
 *     try {
 *         return doOperation();
 *     } catch (Exception e) {
 *         Thread.sleep(policy.delayForAttempt(attempt).toMillis());
 *     }
 * }
 * }</pre>
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double jitterFactor;
    private final Duration maxDuration;

    private RetryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff,
                         double jitterFactor, Duration maxDuration) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitterFactor = jitterFactor;
        this.maxDuration = maxDuration;
    }

    /**
     * Returns a retry policy with sensible defaults.
     *
     * @return default retry policy
     */
    public static RetryPolicy defaults() {
        return builder().build();
    }

    /**
     * Creates a new builder for configuring a retry policy.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Calculates the delay before the given retry attempt.
     *
     * <p>Uses exponential backoff: {@code initialBackoff * 2^attempt}, capped at
     * {@code maxBackoff}. Jitter is applied as:
     * {@code delay * (1 - jitterFactor + random * 2 * jitterFactor)}.
     *
     * @param attempt the zero-based attempt number
     * @return the delay duration for this attempt
     */
    public Duration delayForAttempt(int attempt) {
        long baseMs = initialBackoff.toMillis() * (1L << Math.min(attempt, 30));
        long cappedMs = Math.min(baseMs, maxBackoff.toMillis());

        double jitterMultiplier = 1.0 - jitterFactor
                + ThreadLocalRandom.current().nextDouble() * 2.0 * jitterFactor;
        long delayMs = Math.max(0, (long) (cappedMs * jitterMultiplier));

        return Duration.ofMillis(delayMs);
    }

    /**
     * Returns whether the given attempt number should be retried.
     *
     * @param attempt the zero-based attempt number
     * @return true if the attempt is below the maximum retry count
     */
    public boolean shouldRetry(int attempt) {
        return attempt < maxRetries;
    }

    /**
     * Returns the maximum number of retries.
     *
     * @return max retries
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Returns the initial backoff duration.
     *
     * @return initial backoff
     */
    public Duration initialBackoff() {
        return initialBackoff;
    }

    /**
     * Returns the maximum backoff duration.
     *
     * @return max backoff
     */
    public Duration maxBackoff() {
        return maxBackoff;
    }

    /**
     * Returns the jitter factor.
     *
     * @return jitter factor between 0.0 and 1.0
     */
    public double jitterFactor() {
        return jitterFactor;
    }

    /**
     * Returns the maximum total retry duration.
     *
     * @return max duration
     */
    public Duration maxDuration() {
        return maxDuration;
    }

    /**
     * Builder for creating {@link RetryPolicy} instances.
     */
    public static class Builder {
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(100);
        private Duration maxBackoff = Duration.ofSeconds(10);
        private double jitterFactor = 0.25;
        private Duration maxDuration = Duration.ofMinutes(2);

        private Builder() {}

        /**
         * Sets the maximum number of retries.
         *
         * @param maxRetries max retries (must be &ge; 0)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the initial backoff duration before the first retry.
         *
         * @param initialBackoff initial delay
         * @return this builder
         */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        /**
         * Sets the maximum backoff duration. Exponential growth is capped at this value.
         *
         * @param maxBackoff maximum delay between retries
         * @return this builder
         */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        /**
         * Sets the jitter factor applied to each delay.
         *
         * @param jitterFactor a value between 0.0 (no jitter) and 1.0 (full jitter)
         * @return this builder
         */
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }

        /**
         * Sets the maximum total duration across all retry attempts.
         *
         * @param maxDuration maximum total retry duration
         * @return this builder
         */
        public Builder maxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        /**
         * Builds the retry policy.
         *
         * @return a new {@link RetryPolicy} instance
         */
        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialBackoff, maxBackoff, jitterFactor, maxDuration);
        }
    }
}
