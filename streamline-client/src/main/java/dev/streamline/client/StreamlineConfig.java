package dev.streamline.client;

import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.security.SecurityProtocol;
import dev.streamline.client.security.SaslConfig;
import dev.streamline.client.security.TlsConfig;

import java.util.Objects;

/**
 * Configuration for the Streamline client.
 */
public class StreamlineConfig {

    private final String bootstrapServers;
    private final ProducerConfig producerConfig;
    private final ConsumerConfig consumerConfig;
    private final int connectionPoolSize;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final SecurityProtocol securityProtocol;
    private final SaslConfig saslConfig;
    private final TlsConfig tlsConfig;

    /**
     * Legacy constructor preserving backward compatibility with the original record signature.
     */
    public StreamlineConfig(
            String bootstrapServers,
            ProducerConfig producerConfig,
            ConsumerConfig consumerConfig,
            int connectionPoolSize,
            int connectTimeoutMs,
            int requestTimeoutMs
    ) {
        this(bootstrapServers, producerConfig, consumerConfig,
             connectionPoolSize, connectTimeoutMs, requestTimeoutMs,
             SecurityProtocol.PLAINTEXT, null, null);
    }

    private StreamlineConfig(
            String bootstrapServers,
            ProducerConfig producerConfig,
            ConsumerConfig consumerConfig,
            int connectionPoolSize,
            int connectTimeoutMs,
            int requestTimeoutMs,
            SecurityProtocol securityProtocol,
            SaslConfig saslConfig,
            TlsConfig tlsConfig
    ) {
        this.bootstrapServers = bootstrapServers;
        this.producerConfig = producerConfig;
        this.consumerConfig = consumerConfig;
        this.connectionPoolSize = connectionPoolSize;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.securityProtocol = securityProtocol;
        this.saslConfig = saslConfig;
        this.tlsConfig = tlsConfig;
    }

    /**
     * Creates a new builder for configuring a StreamlineConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Record-style accessors for backward compatibility

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public ProducerConfig producerConfig() {
        return producerConfig;
    }

    public ConsumerConfig consumerConfig() {
        return consumerConfig;
    }

    public int connectionPoolSize() {
        return connectionPoolSize;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int requestTimeoutMs() {
        return requestTimeoutMs;
    }

    // Getter methods

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

    /**
     * Returns the security protocol.
     */
    public SecurityProtocol getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * Returns the SASL configuration, or {@code null} if SASL is not configured.
     */
    public SaslConfig getSaslConfig() {
        return saslConfig;
    }

    /**
     * Returns the TLS configuration, or {@code null} if TLS is not configured.
     */
    public TlsConfig getTlsConfig() {
        return tlsConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamlineConfig that)) return false;
        return connectionPoolSize == that.connectionPoolSize
            && connectTimeoutMs == that.connectTimeoutMs
            && requestTimeoutMs == that.requestTimeoutMs
            && Objects.equals(bootstrapServers, that.bootstrapServers)
            && Objects.equals(producerConfig, that.producerConfig)
            && Objects.equals(consumerConfig, that.consumerConfig)
            && securityProtocol == that.securityProtocol
            && Objects.equals(saslConfig, that.saslConfig)
            && Objects.equals(tlsConfig, that.tlsConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bootstrapServers, producerConfig, consumerConfig,
            connectionPoolSize, connectTimeoutMs, requestTimeoutMs,
            securityProtocol, saslConfig, tlsConfig);
    }

    @Override
    public String toString() {
        return "StreamlineConfig[bootstrapServers=" + bootstrapServers
            + ", connectionPoolSize=" + connectionPoolSize
            + ", securityProtocol=" + securityProtocol + "]";
    }

    /**
     * Builder for {@link StreamlineConfig}.
     */
    public static class Builder {
        private String bootstrapServers;
        private ProducerConfig producerConfig = ProducerConfig.defaults();
        private ConsumerConfig consumerConfig = ConsumerConfig.defaults();
        private int connectionPoolSize = 4;
        private int connectTimeoutMs = 30000;
        private int requestTimeoutMs = 30000;
        private SecurityProtocol securityProtocol = SecurityProtocol.PLAINTEXT;
        private SaslConfig saslConfig;
        private TlsConfig tlsConfig;

        private Builder() {}

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder producerConfig(ProducerConfig producerConfig) {
            this.producerConfig = producerConfig;
            return this;
        }

        public Builder consumerConfig(ConsumerConfig consumerConfig) {
            this.consumerConfig = consumerConfig;
            return this;
        }

        public Builder connectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder requestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder securityProtocol(SecurityProtocol securityProtocol) {
            this.securityProtocol = securityProtocol;
            return this;
        }

        public Builder saslConfig(SaslConfig saslConfig) {
            this.saslConfig = saslConfig;
            return this;
        }

        public Builder tlsConfig(TlsConfig tlsConfig) {
            this.tlsConfig = tlsConfig;
            return this;
        }

        public StreamlineConfig build() {
            Objects.requireNonNull(bootstrapServers, "bootstrapServers must be set");
            return new StreamlineConfig(
                bootstrapServers, producerConfig, consumerConfig,
                connectionPoolSize, connectTimeoutMs, requestTimeoutMs,
                securityProtocol, saslConfig, tlsConfig
            );
        }
    }
}

