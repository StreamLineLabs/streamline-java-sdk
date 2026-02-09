package dev.streamline.client;

import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.consumer.ConsumerConfig;

/**
 * Configuration for the Streamline client.
 */
public record StreamlineConfig(
    String bootstrapServers,
    ProducerConfig producerConfig,
    ConsumerConfig consumerConfig,
    int connectionPoolSize,
    int connectTimeoutMs,
    int requestTimeoutMs
) {

    /**
     * Returns the bootstrap servers.
     */
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Returns the producer configuration.
     */
    public ProducerConfig getProducerConfig() {
        return producerConfig;
    }

    /**
     * Returns the consumer configuration.
     */
    public ConsumerConfig getConsumerConfig() {
        return consumerConfig;
    }

    /**
     * Returns the connection pool size.
     */
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Returns the connection timeout in milliseconds.
     */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /**
     * Returns the request timeout in milliseconds.
     */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
}
