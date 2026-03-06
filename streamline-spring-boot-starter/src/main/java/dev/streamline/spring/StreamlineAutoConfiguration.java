package dev.streamline.spring;

import dev.streamline.client.Streamline;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.schema.SchemaRegistryClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Streamline client.
 */
@AutoConfiguration
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
    public StreamlineTemplate streamlineTemplate(Streamline streamline) {
        return new StreamlineTemplate(streamline);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "streamline", name = "schema-registry-url")
    public SchemaRegistryClient schemaRegistryClient(StreamlineProperties properties) {
        return new SchemaRegistryClient(properties.getSchemaRegistryUrl());
    }
}
