package dev.streamline.client.producer;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.Headers;
import dev.streamline.client.RecordMetadata;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.testsupport.IntegrationEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Producer tests that need a live broker to acknowledge writes.
 *
 * <p>Run with {@code STREAMLINE_INTEGRATION=1 mvn verify -Pintegration}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = IntegrationEnvironment.ENABLED_VAR, matches = "1",
        disabledReason = "set STREAMLINE_INTEGRATION=1 and run with -Pintegration")
class ProducerIT {

    private static final int SEND_TIMEOUT_SECONDS = 30;

    private ConnectionPool connectionPool;
    private Producer<String, String> producer;

    @BeforeEach
    void setUp() {
        IntegrationEnvironment.requireAvailable();

        StreamlineConfig config = new StreamlineConfig(
                IntegrationEnvironment.bootstrapServers(),
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
            try {
                producer.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
        if (connectionPool != null) {
            connectionPool.close();
        }
    }

    private static RecordMetadata await(CompletableFuture<RecordMetadata> future) throws Exception {
        return future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void testSendWithKeyValue() throws Exception {
        RecordMetadata metadata = await(producer.send("it-producer-kv", "key", "value"));

        assertNotNull(metadata);
        assertEquals("it-producer-kv", metadata.topic());
    }

    @Test
    void testSendWithHeaders() throws Exception {
        Headers headers = Headers.builder()
                .add("trace-id", "123")
                .build();

        RecordMetadata metadata = await(producer.send("it-producer-headers", "key", "value", headers));

        assertNotNull(metadata);
        assertEquals("it-producer-headers", metadata.topic());
    }

    @Test
    void testSendToPartition() throws Exception {
        RecordMetadata metadata = await(producer.send("it-producer-partition", 0, "key", "value"));

        assertNotNull(metadata);
        assertEquals("it-producer-partition", metadata.topic());
        assertEquals(0, metadata.partition());
    }

    @Test
    void testSendToPartitionWithHeaders() throws Exception {
        Headers headers = Headers.builder().add("h1", "v1").build();

        RecordMetadata metadata =
                await(producer.send("it-producer-partition-headers", 0, "key", "value", headers));

        assertNotNull(metadata);
        assertEquals(0, metadata.partition());
    }

    @Test
    void testSendReturnsMetadata() throws Exception {
        RecordMetadata metadata = await(producer.send("it-producer-metadata", "key", "value"));

        assertEquals("it-producer-metadata", metadata.topic());
        assertTrue(metadata.offset() >= 0, "offset should be assigned");
        assertTrue(metadata.timestamp() > 0, "timestamp should be assigned");
    }

    @Test
    void testSendWithNullKeyIsAllowed() throws Exception {
        assertNotNull(await(producer.send("it-producer-null-key", null, "value")));
    }

    @Test
    void testSendBatchDeliversEveryRecord() throws Exception {
        List<Map.Entry<String, String>> messages = List.of(
                new AbstractMap.SimpleEntry<>("k1", "v1"),
                new AbstractMap.SimpleEntry<>("k2", "v2"),
                new AbstractMap.SimpleEntry<>("k3", "v3")
        );

        List<CompletableFuture<RecordMetadata>> futures =
                producer.sendBatch("it-producer-batch", messages);

        assertEquals(3, futures.size());
        for (CompletableFuture<RecordMetadata> future : futures) {
            RecordMetadata metadata = await(future);
            assertNotNull(metadata);
            assertEquals("it-producer-batch", metadata.topic());
        }
    }
}
