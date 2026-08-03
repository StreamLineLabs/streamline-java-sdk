package dev.streamline.client;

import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.testsupport.UnitTestEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Streamline}.
 *
 * <p>Only covers builder wiring and lifecycle. Anything that produces to a broker
 * lives in {@code StreamlineIT}.
 */
class StreamlineTest {

    private Streamline client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testBuilderRequiresBootstrapServers() {
        assertThrows(NullPointerException.class, () -> Streamline.builder().build());
    }

    @Test
    void testBuilderWithDefaults() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        assertNotNull(client);
        assertNotNull(client.getConfig());
        assertEquals(UnitTestEndpoints.BOOTSTRAP_SERVERS, client.getConfig().getBootstrapServers());
        assertEquals(4, client.getConfig().getConnectionPoolSize());
        assertEquals(30000, client.getConfig().getConnectTimeoutMs());
        assertEquals(30000, client.getConfig().getRequestTimeoutMs());
    }

    @Test
    void testBuilderWithCustomConfig() {
        client = Streamline.builder()
            .bootstrapServers("host1:9092,host2:9092")
            .connectionPoolSize(8)
            .connectTimeout(10000)
            .requestTimeout(5000)
            .producer(p -> p.batchSize(32768).compressionType("gzip"))
            .consumer(c -> c.groupId("test-group").maxPollRecords(100))
            .build();

        StreamlineConfig config = client.getConfig();
        assertEquals("host1:9092,host2:9092", config.getBootstrapServers());
        assertEquals(8, config.getConnectionPoolSize());
        assertEquals(10000, config.getConnectTimeoutMs());
        assertEquals(5000, config.getRequestTimeoutMs());
        assertEquals(32768, config.getProducerConfig().batchSize());
        assertEquals("gzip", config.getProducerConfig().compressionType());
        assertEquals("test-group", config.getConsumerConfig().groupId());
        assertEquals(100, config.getConsumerConfig().maxPollRecords());
    }

    @Test
    void testCreateProducer() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        try (Producer<String, String> producer = client.createProducer()) {
            assertNotNull(producer);
        }
    }

    @Test
    void testCreateProducerWithConfig() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        ProducerConfig config = ProducerConfig.builder()
            .batchSize(32768)
            .compressionType("snappy")
            .build();

        try (Producer<String, String> producer = client.createProducer(config)) {
            assertNotNull(producer);
        }
    }

    @Test
    void testConsumerCreation() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        try (Consumer<String, String> consumer = client.consumer("test-topic", "test-group")) {
            assertNotNull(consumer);
        }
    }

    @Test
    void testConsumerCreationWithConfig() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        ConsumerConfig config = ConsumerConfig.builder()
            .groupId("custom-group")
            .autoOffsetReset("latest")
            .enableAutoCommit(false)
            .build();

        try (Consumer<String, String> consumer = client.consumer("test-topic", config)) {
            assertNotNull(consumer);
        }
    }

    @Test
    void testIsHealthy() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        assertTrue(client.isHealthy());
    }

    @Test
    void testIsNotHealthyAfterClose() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        client.close();
        assertFalse(client.isHealthy());
    }

    @Test
    void testCloseIdempotent() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        client.close();
        assertDoesNotThrow(() -> client.close());
        assertFalse(client.isHealthy());
    }

    @Test
    void testOperationsAfterClose() {
        client = Streamline.builder()
            .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS)
            .build();

        client.close();

        assertThrows(IllegalStateException.class, () -> client.produce("topic", "key", "value"));
        assertThrows(IllegalStateException.class, () -> client.produceAsync("topic", "key", "value"));
        assertThrows(IllegalStateException.class, () -> client.createProducer());
        assertThrows(IllegalStateException.class, () -> client.consumer("topic", "group"));
    }
}
