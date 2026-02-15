package dev.streamline.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Streamline.
 *
 * <p>Configure in application.yml or application.properties:
 * <pre>
 * streamline:
 *   bootstrap-servers: localhost:9092
 *   producer:
 *     batch-size: 16384
 *     linger-ms: 1
 *   consumer:
 *     group-id: my-app
 *     auto-offset-reset: earliest
 * </pre>
 */
@ConfigurationProperties(prefix = "streamline")
public class StreamlineProperties {

    /**
     * Bootstrap servers (comma-separated list of host:port).
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Connection pool size.
     */
    private int connectionPoolSize = 4;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 30000;

    /**
     * Request timeout in milliseconds.
     */
    private int requestTimeoutMs = 30000;

    /**
     * Producer configuration.
     */
    private ProducerProperties producer = new ProducerProperties();

    /**
     * Consumer configuration.
     */
    private ConsumerProperties consumer = new ConsumerProperties();

    // Getters and setters

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public ProducerProperties getProducer() {
        return producer;
    }

    public void setProducer(ProducerProperties producer) {
        this.producer = producer;
    }

    public ConsumerProperties getConsumer() {
        return consumer;
    }

    public void setConsumer(ConsumerProperties consumer) {
        this.consumer = consumer;
    }

    /**
     * Producer-specific configuration.
     */
    public static class ProducerProperties {
        private int batchSize = 16384;
        private int lingerMs = 1;
        private int maxRequestSize = 1048576;
        private String compressionType = "none";
        private int retries = 3;
        private int retryBackoffMs = 100;
        private boolean idempotent = false;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getLingerMs() { return lingerMs; }
        public void setLingerMs(int lingerMs) { this.lingerMs = lingerMs; }

        public int getMaxRequestSize() { return maxRequestSize; }
        public void setMaxRequestSize(int maxRequestSize) { this.maxRequestSize = maxRequestSize; }

        public String getCompressionType() { return compressionType; }
        public void setCompressionType(String compressionType) { this.compressionType = compressionType; }

        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }

        public int getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(int retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

        public boolean isIdempotent() { return idempotent; }
        public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
    }

    /**
     * Consumer-specific configuration.
     */
    public static class ConsumerProperties {
        private String groupId;
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = true;
        private int autoCommitIntervalMs = 5000;
        private int sessionTimeoutMs = 30000;
        private int heartbeatIntervalMs = 3000;
        private int maxPollRecords = 500;
        private int maxPollIntervalMs = 300000;

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getAutoOffsetReset() { return autoOffsetReset; }
        public void setAutoOffsetReset(String autoOffsetReset) { this.autoOffsetReset = autoOffsetReset; }

        public boolean isEnableAutoCommit() { return enableAutoCommit; }
        public void setEnableAutoCommit(boolean enableAutoCommit) { this.enableAutoCommit = enableAutoCommit; }

        public int getAutoCommitIntervalMs() { return autoCommitIntervalMs; }
        public void setAutoCommitIntervalMs(int autoCommitIntervalMs) { this.autoCommitIntervalMs = autoCommitIntervalMs; }

        public int getSessionTimeoutMs() { return sessionTimeoutMs; }
        public void setSessionTimeoutMs(int sessionTimeoutMs) { this.sessionTimeoutMs = sessionTimeoutMs; }

        public int getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(int heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

        public int getMaxPollRecords() { return maxPollRecords; }
        public void setMaxPollRecords(int maxPollRecords) { this.maxPollRecords = maxPollRecords; }

        public int getMaxPollIntervalMs() { return maxPollIntervalMs; }
        public void setMaxPollIntervalMs(int maxPollIntervalMs) { this.maxPollIntervalMs = maxPollIntervalMs; }
    }
}
