package dev.streamline.client.consumer;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.testsupport.UnitTestEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.streamline.client.StreamlineException;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Consumer}.
 *
 * <p>Only covers behaviour that is decided client-side. Anything that requires a
 * partition assignment from a broker lives in {@code ConsumerIT}.
 */
class ConsumerTest {

    private ConnectionPool connectionPool;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        StreamlineConfig config = new StreamlineConfig(
            UnitTestEndpoints.BOOTSTRAP_SERVERS,
            ProducerConfig.defaults(),
            ConsumerConfig.defaults(),
            4, 30000, 30000
        );
        connectionPool = new ConnectionPool(config);
        ConsumerConfig consumerConfig = ConsumerConfig.builder().groupId("test-group").build();
        consumer = new Consumer<>(connectionPool, config, "test-topic", consumerConfig);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        connectionPool.close();
    }

    @Test
    void testSubscribe() {
        assertDoesNotThrow(() -> consumer.subscribe());
    }

    @Test
    void testPollReturnsEmptyList() {
        consumer.subscribe();
        List<ConsumerRecord<String, String>> records = consumer.poll(Duration.ofMillis(100));

        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    void testPollWithoutSubscribe() {
        assertThrows(IllegalStateException.class, () -> consumer.poll(Duration.ofMillis(100)));
    }

    @Test
    void testCommitSync() {
        assertDoesNotThrow(() -> consumer.commitSync());
    }

    @Test
    void testCommitAsync() {
        assertDoesNotThrow(() -> consumer.commitAsync());
    }

    @Test
    void testSeekToBeginning() {
        assertDoesNotThrow(() -> consumer.seekToBeginning());
    }

    @Test
    void testSeekToEnd() {
        assertDoesNotThrow(() -> consumer.seekToEnd());
    }

    @Test
    void testSeekWithoutAssignmentThrows() {
        assertThrows(IllegalStateException.class, () -> consumer.seek(0, 100L));
    }

    @Test
    void testPositionWithoutAssignmentThrows() {
        assertThrows(IllegalStateException.class, () -> consumer.position(0));
    }

    @Test
    void testPause() {
        assertDoesNotThrow(() -> consumer.pause());
    }

    @Test
    void testResume() {
        assertDoesNotThrow(() -> consumer.resume());
    }

    @Test
    void testClose() {
        assertDoesNotThrow(() -> consumer.close());
    }

    @Test
    void testAssignment() {
        assertNotNull(consumer.assignment());
        assertTrue(consumer.assignment().isEmpty());
    }

    @Test
    void testAssignmentAfterClose() {
        consumer.close();
        assertThrows(IllegalStateException.class, () -> consumer.assignment());
    }

    @Test
    void testSubscription() {
        assertNotNull(consumer.subscription());
        assertTrue(consumer.subscription().isEmpty());
    }

    @Test
    void testSubscriptionAfterClose() {
        consumer.close();
        assertThrows(IllegalStateException.class, () -> consumer.subscription());
    }

    @Test
    void testAssignPartitions() {
        List<TopicPartition> partitions = Collections.singletonList(new TopicPartition("test-topic", 0));
        assertDoesNotThrow(() -> consumer.assign(partitions));
        assertEquals(1, consumer.assignment().size());
    }

    @Test
    void testAssignNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> consumer.assign(null));
    }

    @Test
    void testSeekToTimestampNoPartitions() {
        assertThrows(StreamlineException.class, () -> consumer.seekToTimestamp(System.currentTimeMillis()));
    }

    @Test
    void testListTopicsAfterClose() {
        consumer.close();
        assertThrows(IllegalStateException.class, () -> consumer.listTopics());
    }

    @Test
    void testOperationsAfterClose() {
        consumer.close();

        assertThrows(IllegalStateException.class, () -> consumer.subscribe());
        assertThrows(IllegalStateException.class, () -> consumer.poll(Duration.ofMillis(100)));
        assertThrows(IllegalStateException.class, () -> consumer.commitSync());
        assertThrows(IllegalStateException.class, () -> consumer.commitAsync());
        assertThrows(IllegalStateException.class, () -> consumer.seekToBeginning());
        assertThrows(IllegalStateException.class, () -> consumer.seekToEnd());
        assertThrows(IllegalStateException.class, () -> consumer.seek(0, 0L));
        assertThrows(IllegalStateException.class, () -> consumer.position(0));
        assertThrows(IllegalStateException.class, () -> consumer.pause());
        assertThrows(IllegalStateException.class, () -> consumer.resume());
    }
}
