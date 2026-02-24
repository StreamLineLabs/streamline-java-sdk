package dev.streamline.client.consumer;

import dev.streamline.client.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Consumer for reading messages from Streamline.
 *
 * <p>Delegates to the Apache Kafka client library for wire protocol compatibility.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Consumer<String, String> consumer = client.consumer("my-topic", "my-group")) {
 *     consumer.subscribe();
 *     while (running) {
 *         List<ConsumerRecord<String, String>> records = consumer.poll(Duration.ofMillis(100));
 *         for (ConsumerRecord<String, String> record : records) {
 *             System.out.println(record.value());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class Consumer<K, V> implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private final ConnectionPool connectionPool;
    private final StreamlineConfig config;
    private final String topic;
    private final ConsumerConfig consumerConfig;
    private final KafkaConsumer<byte[], byte[]> kafkaConsumer;
    private volatile boolean subscribed = false;
    private volatile boolean closed = false;

    public Consumer(ConnectionPool connectionPool, StreamlineConfig config, String topic, ConsumerConfig consumerConfig) {
        this.connectionPool = connectionPool;
        this.config = config;
        this.topic = topic;
        this.consumerConfig = consumerConfig;

        Properties props = new Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.autoOffsetReset());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(consumerConfig.enableAutoCommit()));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, String.valueOf(consumerConfig.autoCommitIntervalMs()));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(consumerConfig.sessionTimeoutMs()));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, String.valueOf(consumerConfig.heartbeatIntervalMs()));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(consumerConfig.maxPollRecords()));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(consumerConfig.maxPollIntervalMs()));

        if (consumerConfig.groupId() != null) {
            props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, consumerConfig.groupId());
        }

        this.kafkaConsumer = new KafkaConsumer<>(props);
        log.debug("Consumer created for topic {} with group {}", topic, consumerConfig.groupId());
    }

    /**
     * Subscribes to the topic.
     */
    public void subscribe() {
        ensureOpen();
        if (!subscribed) {
            kafkaConsumer.subscribe(Collections.singletonList(topic));
            subscribed = true;
            log.info("Subscribed to topic {}", topic);
        }
    }

    /**
     * Polls for new records.
     *
     * @param timeout the maximum time to wait for records
     * @return a list of records (may be empty)
     */
    @SuppressWarnings("unchecked")
    public List<ConsumerRecord<K, V>> poll(Duration timeout) {
        ensureOpen();
        if (!subscribed) {
            throw new IllegalStateException("Consumer is not subscribed");
        }

        var kafkaRecords = kafkaConsumer.poll(timeout);
        List<ConsumerRecord<K, V>> results = new ArrayList<>();

        for (var record : kafkaRecords) {
            K key = record.key() != null ? (K) new String(record.key(), StandardCharsets.UTF_8) : null;
            V value = record.value() != null ? (V) new String(record.value(), StandardCharsets.UTF_8) : null;

            Headers headers = Headers.empty();
            if (record.headers() != null) {
                Headers.Builder hb = Headers.builder();
                for (var header : record.headers()) {
                    hb.add(header.key(), new String(header.value(), StandardCharsets.UTF_8));
                }
                headers = hb.build();
            }

            results.add(new ConsumerRecord<>(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                key,
                value,
                headers
            ));
        }

        return results;
    }

    /**
     * Commits the current offsets synchronously.
     */
    public void commitSync() {
        ensureOpen();
        kafkaConsumer.commitSync();
        log.debug("Committed offsets synchronously");
    }

    /**
     * Commits the current offsets asynchronously.
     */
    public void commitAsync() {
        ensureOpen();
        kafkaConsumer.commitAsync((offsets, exception) -> {
            if (exception != null) {
                log.warn("Async offset commit failed", exception);
            } else {
                log.debug("Async commit completed");
            }
        });
    }

    /**
     * Seeks to the beginning of all partitions.
     */
    public void seekToBeginning() {
        ensureOpen();
        kafkaConsumer.seekToBeginning(kafkaConsumer.assignment());
        log.debug("Seeking to beginning");
    }

    /**
     * Seeks to the end of all partitions.
     */
    public void seekToEnd() {
        ensureOpen();
        kafkaConsumer.seekToEnd(kafkaConsumer.assignment());
        log.debug("Seeking to end");
    }

    /**
     * Seeks to a specific offset.
     *
     * @param partition the partition
     * @param offset    the offset
     */
    public void seek(int partition, long offset) {
        ensureOpen();
        kafkaConsumer.seek(new TopicPartition(topic, partition), offset);
        log.debug("Seeking partition {} to offset {}", partition, offset);
    }

    /**
     * Returns the current position for a partition.
     *
     * @param partition the partition
     * @return the current offset position
     */
    public long position(int partition) {
        ensureOpen();
        return kafkaConsumer.position(new TopicPartition(topic, partition));
    }

    /**
     * Pauses consumption.
     */
    public void pause() {
        ensureOpen();
        kafkaConsumer.pause(kafkaConsumer.assignment());
        log.debug("Consumer paused");
    }

    /**
     * Resumes consumption.
     */
    public void resume() {
        ensureOpen();
        kafkaConsumer.resume(kafkaConsumer.assignment());
        log.debug("Consumer resumed");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Consumer is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            kafkaConsumer.close(Duration.ofSeconds(30));
            closed = true;
            log.info("Consumer closed");
        }
    }
}
