package dev.streamline.client.producer;

import dev.streamline.client.*;
import dev.streamline.client.consumer.ConsumerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ProducerTest {

    private ConnectionPool connectionPool;
    private StreamlineConfig config;
    private Producer<String, String> producer;

    @BeforeEach
    void setUp() {
        config = new StreamlineConfig(
            "localhost:9092",
            ProducerConfig.defaults(),
            ConsumerConfig.defaults(),
            4, 30000, 30000
        );
        connectionPool = new ConnectionPool(config);
        producer = new Producer<>(connectionPool, config, ProducerConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            try { producer.close(); } catch (Exception ignored) {}
        }
        connectionPool.close();
    }

    @Test
    void testSendWithKeyValue() throws ExecutionException, InterruptedException {
        CompletableFuture<RecordMetadata> future = producer.send("test-topic", "key", "value");

        assertNotNull(future);
        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
        assertEquals("test-topic", metadata.topic());
    }

    @Test
    void testSendWithHeaders() throws ExecutionException, InterruptedException {
        Headers headers = Headers.builder()
            .add("trace-id", "123")
            .build();

        CompletableFuture<RecordMetadata> future = producer.send("topic", "key", "value", headers);

        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
        assertEquals("topic", metadata.topic());
    }

    @Test
    void testSendToPartition() throws ExecutionException, InterruptedException {
        CompletableFuture<RecordMetadata> future = producer.send("topic", 2, "key", "value");

        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
        assertEquals("topic", metadata.topic());
        assertEquals(2, metadata.partition());
    }

    @Test
    void testSendToPartitionWithHeaders() throws ExecutionException, InterruptedException {
        Headers headers = Headers.builder().add("h1", "v1").build();
        CompletableFuture<RecordMetadata> future = producer.send("topic", 5, "key", "value", headers);

        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
        assertEquals(5, metadata.partition());
    }

    @Test
    void testFlush() {
        assertDoesNotThrow(() -> producer.flush());
    }

    @Test
    void testClose() {
        assertDoesNotThrow(() -> producer.close());
    }

    @Test
    void testSendAfterClose() {
        producer.close();
        assertThrows(IllegalStateException.class, () -> producer.send("topic", "key", "value"));
    }

    @Test
    void testSendReturnsMetadata() throws ExecutionException, InterruptedException {
        RecordMetadata metadata = producer.send("my-topic", "key", "value").get();

        assertEquals("my-topic", metadata.topic());
        assertEquals(0, metadata.partition());
        assertTrue(metadata.offset() > 0);
        assertTrue(metadata.timestamp() > 0);
    }

    @Test
    void testConstructionWithDefaultConfig() {
        Producer<String, String> p = new Producer<>(connectionPool, config, ProducerConfig.defaults());
        assertNotNull(p);
        p.close();
    }

    @Test
    void testConstructionWithCustomConfig() {
        ProducerConfig custom = ProducerConfig.builder()
            .batchSize(32768)
            .lingerMs(10)
            .compressionType("gzip")
            .retries(5)
            .idempotent(true)
            .build();
        Producer<String, String> p = new Producer<>(connectionPool, config, custom);
        assertNotNull(p);
        p.close();
    }

    @Test
    void testSendWithNullTopicShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.send(null, "key", "value"));
    }

    @Test
    void testSendWithNullValueShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.send("topic", "key", null));
    }

    @Test
    void testSendToPartitionWithNullTopicShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.send(null, 0, "key", "value"));
    }

    @Test
    void testSendToPartitionWithNullValueShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.send("topic", 0, "key", null));
    }

    @Test
    void testCloseIsIdempotent() {
        producer.close();
        assertDoesNotThrow(() -> producer.close());
    }

    @Test
    void testFlushAfterCloseShouldThrow() {
        producer.close();
        assertThrows(IllegalStateException.class, () -> producer.flush());
    }

    @Test
    void testSendWithNullKeyIsAllowed() throws ExecutionException, InterruptedException {
        CompletableFuture<RecordMetadata> future = producer.send("topic", null, "value");
        assertNotNull(future);
        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
    }
}
