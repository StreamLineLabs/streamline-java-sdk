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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final CircuitBreaker circuitBreaker;
    private volatile boolean closed = false;

    public Producer(ConnectionPool connectionPool, StreamlineConfig config, ProducerConfig producerConfig) {
        this(connectionPool, config, producerConfig, null);
    }

    public Producer(ConnectionPool connectionPool, StreamlineConfig config, ProducerConfig producerConfig, CircuitBreaker circuitBreaker) {
        this.connectionPool = connectionPool;
        this.config = config;
        this.producerConfig = producerConfig;
        this.circuitBreaker = circuitBreaker;

        Properties props = new Properties();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BATCH_SIZE_CONFIG, producerConfig.batchSize());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.LINGER_MS_CONFIG, producerConfig.lingerMs());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
        props.put(org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG, producerConfig.retries());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG, producerConfig.retryBackoffMs());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.COMPRESSION_TYPE_CONFIG, producerConfig.compressionType());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producerConfig.idempotent());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.MAX_REQUEST_SIZE_CONFIG, producerConfig.maxRequestSize());

        if (producerConfig.transactionalId() != null) {
            props.put(org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG, producerConfig.transactionalId());
            props.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        }

        this.kafkaProducer = new KafkaProducer<>(props);

        if (producerConfig.transactionalId() != null) {
            kafkaProducer.initTransactions();
            log.debug("Transactions initialized with ID '{}'", producerConfig.transactionalId());
        }

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
        TopicNameValidator.validate(topic);
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }

        if (circuitBreaker != null && !circuitBreaker.allow()) {
            CompletableFuture<RecordMetadata> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new StreamlineException(
                "Circuit breaker is open — too many recent failures",
                null,
                true,
                "The client detected repeated failures and is temporarily pausing requests."
            ));
            return rejected;
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
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                future.completeExceptionally(new StreamlineException("Failed to send message", exception));
            } else {
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
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
        TopicNameValidator.validate(topic);
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }

        if (circuitBreaker != null && !circuitBreaker.allow()) {
            CompletableFuture<RecordMetadata> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new StreamlineException(
                "Circuit breaker is open — too many recent failures",
                null,
                true,
                "The client detected repeated failures and is temporarily pausing requests."
            ));
            return rejected;
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
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                future.completeExceptionally(new StreamlineException("Failed to send message", exception));
            } else {
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
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

    /**
     * Begins a new transaction. Must be called before any transactional sends.
     * Requires transactionalId to be set in ProducerConfig.
     *
     * @throws IllegalStateException if transactions are not enabled
     * @throws StreamlineException if beginning the transaction fails
     */
    public void beginTransaction() {
        ensureOpen();
        ensureTransactional();
        try {
            kafkaProducer.beginTransaction();
            log.debug("Transaction started");
        } catch (Exception e) {
            throw new StreamlineException("Failed to begin transaction", e, true,
                "Ensure the transactional ID is unique and the server supports transactions.");
        }
    }

    /**
     * Commits the current transaction. All messages sent since beginTransaction()
     * will be made visible to consumers atomically.
     *
     * @throws StreamlineException if the commit fails
     */
    public void commitTransaction() {
        ensureOpen();
        ensureTransactional();
        try {
            kafkaProducer.commitTransaction();
            log.debug("Transaction committed");
        } catch (Exception e) {
            throw new StreamlineException("Failed to commit transaction", e, true,
                "The transaction may have timed out. Check transaction.timeout.ms configuration.");
        }
    }

    /**
     * Aborts the current transaction. All messages sent since beginTransaction()
     * will be discarded.
     *
     * @throws StreamlineException if the abort fails
     */
    public void abortTransaction() {
        ensureOpen();
        ensureTransactional();
        try {
            kafkaProducer.abortTransaction();
            log.debug("Transaction aborted");
        } catch (Exception e) {
            throw new StreamlineException("Failed to abort transaction", e, false,
                "Transaction abort failed. The producer may need to be recreated.");
        }
    }

    /**
     * Sends a batch of messages to the specified topic.
     *
     * @param topic    the topic name
     * @param messages list of key-value pairs to send
     * @return a list of futures, one per message
     */
    public List<CompletableFuture<RecordMetadata>> sendBatch(String topic, List<Map.Entry<K, V>> messages) {
        ensureOpen();
        TopicNameValidator.validate(topic);
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("Messages must not be empty");

        List<CompletableFuture<RecordMetadata>> futures = new ArrayList<>(messages.size());
        for (Map.Entry<K, V> entry : messages) {
            futures.add(send(topic, entry.getKey(), entry.getValue()));
        }
        return futures;
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

    private void ensureTransactional() {
        if (producerConfig.transactionalId() == null) {
            throw new IllegalStateException(
                "Transactions are not enabled. Set transactionalId in ProducerConfig to use transactions.");
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
