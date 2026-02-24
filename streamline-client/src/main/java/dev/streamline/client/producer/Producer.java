package dev.streamline.client.producer;

import dev.streamline.client.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous producer for sending messages to Streamline.
 *
 * <p>Delegates to the Apache Kafka client library for wire protocol compatibility.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (Producer<String, String> producer = client.createProducer()) {
 *     CompletableFuture<RecordMetadata> future = producer.send("topic", "key", "value");
 *     RecordMetadata metadata = future.get();
 *     System.out.println("Sent to partition " + metadata.partition() + " at offset " + metadata.offset());
 * }
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class Producer<K, V> implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(Producer.class);

    private final ConnectionPool connectionPool;
    private final StreamlineConfig config;
    private final ProducerConfig producerConfig;
    private final KafkaProducer<byte[], byte[]> kafkaProducer;
    private volatile boolean closed = false;

    public Producer(ConnectionPool connectionPool, StreamlineConfig config, ProducerConfig producerConfig) {
        this.connectionPool = connectionPool;
        this.config = config;
        this.producerConfig = producerConfig;

        Properties props = new Properties();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BATCH_SIZE_CONFIG, producerConfig.batchSize());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.LINGER_MS_CONFIG, producerConfig.lingerMs());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
        props.put(org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG, 3);

        this.kafkaProducer = new KafkaProducer<>(props);
        log.debug("Producer created with batch size {}", producerConfig.batchSize());
    }

    /**
     * Sends a message to the specified topic.
     *
     * @param topic the topic name
     * @param key   the message key (may be null)
     * @param value the message value
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> send(String topic, K key, V value) {
        return send(topic, key, value, null);
    }

    /**
     * Sends a message to the specified topic with headers.
     *
     * @param topic   the topic name
     * @param key     the message key (may be null)
     * @param value   the message value
     * @param headers the message headers (may be null)
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> send(String topic, K key, V value, Headers headers) {
        ensureOpen();
        if (topic == null) {
            throw new IllegalArgumentException("Topic must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }

        byte[] keyBytes = key != null ? serializeToBytes(key) : null;
        byte[] valueBytes = serializeToBytes(value);

        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, keyBytes, valueBytes);
        if (headers != null) {
            for (var entry : headers.toMap().entrySet()) {
                record.headers().add(new RecordHeader(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8)));
            }
        }

        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(new StreamlineException("Failed to send message", exception));
            } else {
                future.complete(new RecordMetadata(
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    metadata.timestamp()
                ));
            }
        });

        return future;
    }

    /**
     * Sends a message to a specific partition.
     *
     * @param topic     the topic name
     * @param partition the partition number
     * @param key       the message key (may be null)
     * @param value     the message value
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> send(String topic, int partition, K key, V value) {
        return send(topic, partition, key, value, null);
    }

    /**
     * Sends a message to a specific partition with headers.
     *
     * @param topic     the topic name
     * @param partition the partition number
     * @param key       the message key (may be null)
     * @param value     the message value
     * @param headers   the message headers (may be null)
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> send(String topic, int partition, K key, V value, Headers headers) {
        ensureOpen();
        if (topic == null) {
            throw new IllegalArgumentException("Topic must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }

        byte[] keyBytes = key != null ? serializeToBytes(key) : null;
        byte[] valueBytes = serializeToBytes(value);

        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, partition, keyBytes, valueBytes);
        if (headers != null) {
            for (var entry : headers.toMap().entrySet()) {
                record.headers().add(new RecordHeader(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8)));
            }
        }

        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(new StreamlineException("Failed to send message", exception));
            } else {
                future.complete(new RecordMetadata(
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    metadata.timestamp()
                ));
            }
        });

        return future;
    }

    /**
     * Flushes any buffered messages, blocking until all sends complete.
     */
    public void flush() {
        ensureOpen();
        kafkaProducer.flush();
        log.debug("Producer flushed");
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeToBytes(Object obj) {
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else if (obj instanceof String) {
            return ((String) obj).getBytes(StandardCharsets.UTF_8);
        } else {
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Producer is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            kafkaProducer.close(Duration.ofSeconds(30));
            closed = true;
            log.debug("Producer closed");
        }
    }
}
