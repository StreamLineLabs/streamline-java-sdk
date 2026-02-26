package dev.streamline.client.consumer;

/**
 * Configuration for a Streamline consumer.
 */
public record ConsumerConfig(
    String groupId,
    String autoOffsetReset,
    boolean enableAutoCommit,
    int autoCommitIntervalMs,
    int sessionTimeoutMs,
    int heartbeatIntervalMs,
    int maxPollRecords,
    int maxPollIntervalMs
) {

    /**
     * Returns default consumer configuration.
     */
    public static ConsumerConfig defaults() {
        return new ConsumerConfig(
            null,           // no group by default
            "earliest",     // start from beginning
            true,           // auto-commit enabled
            5000,           // 5s auto-commit interval
            30000,          // 30s session timeout
            3000,           // 3s heartbeat interval
            500,            // 500 records per poll
            300000          // 5 minutes max poll interval
        );
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ConsumerConfig.
     */
    public static class Builder {
        private String groupId;
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = true;
        private int autoCommitIntervalMs = 5000;
        private int sessionTimeoutMs = 30000;
        private int heartbeatIntervalMs = 3000;
        private int maxPollRecords = 500;
        private int maxPollIntervalMs = 300000;

        /**
         * Sets the consumer group ID.
         */
        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        /**
         * Sets the auto offset reset policy (earliest, latest, none).
         */
        public Builder autoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
            return this;
        }

        /**
         * Enables or disables auto-commit.
         */
        public Builder enableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
            return this;
        }

        /**
         * Sets the auto-commit interval in milliseconds.
         */
        public Builder autoCommitIntervalMs(int autoCommitIntervalMs) {
            this.autoCommitIntervalMs = autoCommitIntervalMs;
            return this;
        }

        /**
         * Sets the session timeout in milliseconds.
         */
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        /**
         * Sets the heartbeat interval in milliseconds.
         */
        public Builder heartbeatIntervalMs(int heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        /**
         * Sets the maximum records per poll.
         */
        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        /**
         * Sets the maximum poll interval in milliseconds.
         */
        public Builder maxPollIntervalMs(int maxPollIntervalMs) {
            this.maxPollIntervalMs = maxPollIntervalMs;
            return this;
        }

        /**
         * Builds the configuration.
         */
        public ConsumerConfig build() {
            return new ConsumerConfig(
                groupId, autoOffsetReset, enableAutoCommit,
                autoCommitIntervalMs, sessionTimeoutMs, heartbeatIntervalMs,
                maxPollRecords, maxPollIntervalMs
            );
        }
    }
}
// resolve ClassCastException in header parsing
