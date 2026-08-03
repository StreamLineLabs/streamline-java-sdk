package dev.streamline.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.Streamline;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.schema.SchemaRegistryClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Streamline client.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnClass(Streamline.class)
@EnableConfigurationProperties(StreamlineProperties.class)
public class StreamlineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Streamline streamline(StreamlineProperties properties) {
        StreamlineProperties.ProducerProperties producer = properties.getProducer();
        StreamlineProperties.ConsumerProperties consumer = properties.getConsumer();

        return Streamline.builder()
            .bootstrapServers(properties.getBootstrapServers())
            .connectionPoolSize(properties.getConnectionPoolSize())
            .connectTimeout(properties.getConnectTimeoutMs())
            .requestTimeout(properties.getRequestTimeoutMs())
            .producer(p -> p
                .batchSize(producer.getBatchSize())
                .lingerMs(producer.getLingerMs())
                .maxRequestSize(producer.getMaxRequestSize())
                .compressionType(producer.getCompressionType())
                .retries(producer.getRetries())
                .retryBackoffMs(producer.getRetryBackoffMs())
                .idempotent(producer.isIdempotent()))
            .consumer(c -> c
                .groupId(consumer.getGroupId())
                .autoOffsetReset(consumer.getAutoOffsetReset())
                .enableAutoCommit(consumer.isEnableAutoCommit())
                .autoCommitIntervalMs(consumer.getAutoCommitIntervalMs())
                .sessionTimeoutMs(consumer.getSessionTimeoutMs())
                .heartbeatIntervalMs(consumer.getHeartbeatIntervalMs())
                .maxPollRecords(consumer.getMaxPollRecords())
                .maxPollIntervalMs(consumer.getMaxPollIntervalMs()))
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamlineTemplate streamlineTemplate(Streamline streamline,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        // Jackson's ObjectMapper bean is only auto-configured for web applications, so the
        // starter falls back to the template's own mapper instead of failing to start.
        ObjectMapper mapper = objectMapper.getIfAvailable();
        return mapper == null
            ? new StreamlineTemplate(streamline)
            : new StreamlineTemplate(streamline, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "streamline", name = "schema-registry-url")
    public SchemaRegistryClient schemaRegistryClient(StreamlineProperties properties) {
        return new SchemaRegistryClient(properties.getSchemaRegistryUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public StreamlineHealthIndicator streamlineHealthIndicator(Streamline streamline) {
        return new StreamlineHealthIndicator(streamline);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamlineListenerContainerProcessor streamlineListenerContainerProcessor(Streamline streamline) {
        return new StreamlineListenerContainerProcessor(streamline);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnBean(MeterRegistry.class)
    public StreamlineMetrics streamlineMetrics(MeterRegistry registry) {
        return new StreamlineMetrics(registry);
    }
}
