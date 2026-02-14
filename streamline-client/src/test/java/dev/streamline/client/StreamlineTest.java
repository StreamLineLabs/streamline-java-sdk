package dev.streamline.client;

import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

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
            .bootstrapServers("localhost:9092")
            .build();

        assertNotNull(client);
        assertNotNull(client.getConfig());
        assertEquals("localhost:9092", client.getConfig().getBootstrapServers());
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
    void testProduce() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        RecordMetadata metadata = client.produce("test-topic", "key1", "value1");

        assertNotNull(metadata);
        assertEquals("test-topic", metadata.topic());
        assertEquals(0, metadata.partition());
        assertTrue(metadata.offset() > 0);
        assertTrue(metadata.timestamp() > 0);
    }

    @Test
    void testProduceAsync() throws ExecutionException, InterruptedException {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        CompletableFuture<RecordMetadata> future = client.produceAsync("test-topic", "key1", "value1");

        assertNotNull(future);
        RecordMetadata metadata = future.get();
        assertNotNull(metadata);
        assertEquals("test-topic", metadata.topic());
    }

    @Test
    void testCreateProducer() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        try (Producer<String, String> producer = client.createProducer()) {
            assertNotNull(producer);
        }
    }

    @Test
    void testCreateProducerWithConfig() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
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
            .bootstrapServers("localhost:9092")
            .build();

        try (Consumer<String, String> consumer = client.consumer("test-topic", "test-group")) {
            assertNotNull(consumer);
        }
    }

    @Test
    void testConsumerCreationWithConfig() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
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
            .bootstrapServers("localhost:9092")
            .build();

        assertTrue(client.isHealthy());
    }

    @Test
    void testIsNotHealthyAfterClose() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        client.close();
        assertFalse(client.isHealthy());
    }

    @Test
    void testCloseIdempotent() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        client.close();
        assertDoesNotThrow(() -> client.close());
        assertFalse(client.isHealthy());
    }

    @Test
    void testOperationsAfterClose() {
        client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();

        client.close();

        assertThrows(IllegalStateException.class, () -> client.produce("topic", "key", "value"));
        assertThrows(IllegalStateException.class, () -> client.produceAsync("topic", "key", "value"));
        assertThrows(IllegalStateException.class, () -> client.createProducer());
        assertThrows(IllegalStateException.class, () -> client.consumer("topic", "group"));
    }
}
