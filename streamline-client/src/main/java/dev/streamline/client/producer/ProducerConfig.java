package dev.streamline.client.producer;

/**
 * Configuration for a Streamline producer.
 */
public record ProducerConfig(
    int batchSize,
    int lingerMs,
    int maxRequestSize,
    String compressionType,
    int retries,
    int retryBackoffMs,
    boolean idempotent
) {

    public ProducerConfig {
        if (batchSize < 0) {
            throw new IllegalArgumentException("Batch size must not be negative");
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("Linger ms must not be negative");
        }
        if (maxRequestSize < 0) {
            throw new IllegalArgumentException("Max request size must not be negative");
        }
        if (retries < 0) {
            throw new IllegalArgumentException("Retries must not be negative");
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException("Retry backoff ms must not be negative");
        }
    }

    /**
     * Returns default producer configuration.
     */
    public static ProducerConfig defaults() {
        return new ProducerConfig(
            16384,      // 16KB batch size
            1,          // 1ms linger
            1048576,    // 1MB max request
            "none",     // no compression
            3,          // 3 retries
            100,        // 100ms backoff
            false       // not idempotent by default
        );
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ProducerConfig.
     */
    public static class Builder {
        private int batchSize = 16384;
        private int lingerMs = 1;
        private int maxRequestSize = 1048576;
        private String compressionType = "none";
        private int retries = 3;
        private int retryBackoffMs = 100;
        private boolean idempotent = false;

        /**
         * Sets the batch size in bytes.
         */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the linger time in milliseconds.
         */
        public Builder lingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
            return this;
        }

        /**
         * Sets the maximum request size.
         */
        public Builder maxRequestSize(int maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
            return this;
        }

        /**
         * Sets the compression type (none, gzip, snappy, lz4, zstd).
         */
        public Builder compressionType(String compressionType) {
            this.compressionType = compressionType;
            return this;
        }

        /**
         * Sets the number of retries.
         */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        /**
         * Sets the retry backoff in milliseconds.
         */
        public Builder retryBackoffMs(int retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
            return this;
        }

        /**
         * Enables idempotent producer.
         */
        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        /**
         * Builds the configuration.
         */
        public ProducerConfig build() {
            return new ProducerConfig(
                batchSize, lingerMs, maxRequestSize,
                compressionType, retries, retryBackoffMs, idempotent
            );
        }
    }
}
