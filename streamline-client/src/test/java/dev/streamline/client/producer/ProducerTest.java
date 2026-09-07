package dev.streamline.client.producer;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.testsupport.UnitTestEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Producer}.
 *
 * <p>Only covers behaviour that is decided client-side (validation, lifecycle,
 * configuration). {@code send} blocks until the broker publishes topic metadata, so
 * every successful-send assertion lives in {@code ProducerIT}.
 */
class ProducerTest {

    private ConnectionPool connectionPool;
    private StreamlineConfig config;
    private Producer<String, String> producer;

    @BeforeEach
    void setUp() {
        config = new StreamlineConfig(
            UnitTestEndpoints.BOOTSTRAP_SERVERS,
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

    // --- Transaction tests ---

    @Test
    void testBeginTransactionThrowsWhenTransactionalIdIsNull() {
        assertThrows(IllegalStateException.class, () -> producer.beginTransaction());
    }

    @Test
    void testCommitTransactionThrowsWhenTransactionalIdIsNull() {
        assertThrows(IllegalStateException.class, () -> producer.commitTransaction());
    }

    @Test
    void testAbortTransactionThrowsWhenTransactionalIdIsNull() {
        assertThrows(IllegalStateException.class, () -> producer.abortTransaction());
    }

    @Test
    void testBeginTransactionThrowsWhenProducerIsClosed() {
        producer.close();
        assertThrows(IllegalStateException.class, () -> producer.beginTransaction());
    }

    @Test
    void testCommitTransactionThrowsWhenProducerIsClosed() {
        producer.close();
        assertThrows(IllegalStateException.class, () -> producer.commitTransaction());
    }

    @Test
    void testAbortTransactionThrowsWhenProducerIsClosed() {
        producer.close();
        assertThrows(IllegalStateException.class, () -> producer.abortTransaction());
    }

    // --- sendBatch tests ---

    @Test
    void testSendBatchWithNullTopicShouldThrow() {
        List<Map.Entry<String, String>> messages = List.of(
            new AbstractMap.SimpleEntry<>("k1", "v1")
        );
        assertThrows(IllegalArgumentException.class, () -> producer.sendBatch(null, messages));
    }

    @Test
    void testSendBatchWithNullMessagesShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.sendBatch("topic", null));
    }

    @Test
    void testSendBatchWithEmptyMessagesShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.sendBatch("topic", List.of()));
    }

    @Test
    void testSendBatchAfterCloseShouldThrow() {
        producer.close();
        List<Map.Entry<String, String>> messages = List.of(
            new AbstractMap.SimpleEntry<>("k1", "v1")
        );
        assertThrows(IllegalStateException.class, () -> producer.sendBatch("topic", messages));
    }
}
