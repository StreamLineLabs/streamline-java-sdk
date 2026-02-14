package dev.streamline.client;

import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Main entry point for the Streamline Java client.
 *
 * <p>Example usage:
 * <pre>{@code
 * Streamline client = Streamline.builder()
 *     .bootstrapServers("localhost:9092")
 *     .build();
 *
 * // Produce a message
 * client.produce("my-topic", "key", "value");
 *
 * // Create a consumer
 * try (Consumer<String, String> consumer = client.consumer("my-topic", "my-group")) {
 *     consumer.subscribe();
 *     // Poll for messages...
 * }
 * }</pre>
 */
public class Streamline implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(Streamline.class);

    private final StreamlineConfig config;
    private final ConnectionPool connectionPool;
    private volatile boolean closed = false;

    private Streamline(StreamlineConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.connectionPool = new ConnectionPool(config);
        log.info("Streamline client initialized with bootstrap servers: {}", config.getBootstrapServers());
    }

    /**
     * Creates a new builder for configuring a Streamline client.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Produces a message to the specified topic.
     *
     * @param topic the topic to produce to
     * @param key   the message key (may be null)
     * @param value the message value
     * @return the record metadata (partition and offset)
     */
    public RecordMetadata produce(String topic, String key, String value) {
        return produce(topic, key, value, null);
    }

    /**
     * Produces a message to the specified topic with headers.
     *
     * @param topic   the topic to produce to
     * @param key     the message key (may be null)
     * @param value   the message value
     * @param headers the message headers (may be null)
     * @return the record metadata (partition and offset)
     */
    public RecordMetadata produce(String topic, String key, String value, Headers headers) {
        ensureOpen();
        try (Producer<String, String> producer = createProducer()) {
            return producer.send(topic, key, value, headers).join();
        }
    }

    /**
     * Produces a message asynchronously.
     *
     * @param topic the topic to produce to
     * @param key   the message key (may be null)
     * @param value the message value
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> produceAsync(String topic, String key, String value) {
        ensureOpen();
        Producer<String, String> producer = createProducer();
        return producer.send(topic, key, value, null)
            .whenComplete((metadata, error) -> producer.close());
    }

    /**
     * Creates a new producer with the default configuration.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return a new producer instance
     */
    @SuppressWarnings("unchecked")
    public <K, V> Producer<K, V> createProducer() {
        return createProducer(ProducerConfig.defaults());
    }

    /**
     * Creates a new producer with the specified configuration.
     *
     * @param <K>            the key type
     * @param <V>            the value type
     * @param producerConfig the producer configuration
     * @return a new producer instance
     */
    public <K, V> Producer<K, V> createProducer(ProducerConfig producerConfig) {
        ensureOpen();
        return new Producer<>(connectionPool, config, producerConfig);
    }

    /**
     * Creates a consumer for the specified topic and group.
     *
     * @param <K>     the key type
     * @param <V>     the value type
     * @param topic   the topic to consume from
     * @param groupId the consumer group ID
     * @return a new consumer instance
     */
    public <K, V> Consumer<K, V> consumer(String topic, String groupId) {
        return consumer(topic, ConsumerConfig.builder().groupId(groupId).build());
    }

    /**
     * Creates a consumer with the specified configuration.
     *
     * @param <K>            the key type
     * @param <V>            the value type
     * @param topic          the topic to consume from
     * @param consumerConfig the consumer configuration
     * @return a new consumer instance
     */
    public <K, V> Consumer<K, V> consumer(String topic, ConsumerConfig consumerConfig) {
        ensureOpen();
        return new Consumer<>(connectionPool, config, topic, consumerConfig);
    }

    /**
     * Returns the client configuration.
     *
     * @return the configuration
     */
    public StreamlineConfig getConfig() {
        return config;
    }

    /**
     * Checks if the client is connected and healthy.
     *
     * @return true if the client is healthy
     */
    public boolean isHealthy() {
        return !closed && connectionPool.isHealthy();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Streamline client is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            connectionPool.close();
            log.info("Streamline client closed");
        }
    }

    /**
     * Builder for creating Streamline client instances.
     */
    public static class Builder {
        private String bootstrapServers;
        private ProducerConfig producerConfig = ProducerConfig.defaults();
        private ConsumerConfig consumerConfig = ConsumerConfig.defaults();
        private int connectionPoolSize = 4;
        private int connectTimeoutMs = 30000;
        private int requestTimeoutMs = 30000;

        private Builder() {}

        /**
         * Sets the bootstrap servers.
         *
         * @param bootstrapServers comma-separated list of host:port pairs
         * @return this builder
         */
        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        /**
         * Configures producer settings.
         *
         * @param configurer a function to configure the producer
         * @return this builder
         */
        public Builder producer(java.util.function.Consumer<ProducerConfig.Builder> configurer) {
            ProducerConfig.Builder builder = ProducerConfig.builder();
            configurer.accept(builder);
            this.producerConfig = builder.build();
            return this;
        }

        /**
         * Configures consumer settings.
         *
         * @param configurer a function to configure the consumer
         * @return this builder
         */
        public Builder consumer(java.util.function.Consumer<ConsumerConfig.Builder> configurer) {
            ConsumerConfig.Builder builder = ConsumerConfig.builder();
            configurer.accept(builder);
            this.consumerConfig = builder.build();
            return this;
        }

        /**
         * Sets the connection pool size.
         *
         * @param size the number of connections to maintain
         * @return this builder
         */
        public Builder connectionPoolSize(int size) {
            this.connectionPoolSize = size;
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public Builder connectTimeout(int timeoutMs) {
            this.connectTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the request timeout.
         *
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public Builder requestTimeout(int timeoutMs) {
            this.requestTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Builds the Streamline client.
         *
         * @return a new Streamline client instance
         */
        public Streamline build() {
            Objects.requireNonNull(bootstrapServers, "bootstrapServers must be set");

            StreamlineConfig config = new StreamlineConfig(
                bootstrapServers,
                producerConfig,
                consumerConfig,
                connectionPoolSize,
                connectTimeoutMs,
                requestTimeoutMs
            );

            return new Streamline(config);
        }
    }
}
