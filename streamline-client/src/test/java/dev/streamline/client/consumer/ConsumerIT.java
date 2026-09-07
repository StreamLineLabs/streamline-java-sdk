package dev.streamline.client.consumer;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.testsupport.IntegrationEnvironment;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer tests that need a live broker to resolve partitions and offsets.
 *
 * <p>Run with {@code STREAMLINE_INTEGRATION=1 mvn verify -Pintegration}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = IntegrationEnvironment.ENABLED_VAR, matches = "1",
        disabledReason = "set STREAMLINE_INTEGRATION=1 and run with -Pintegration")
class ConsumerIT {

    private static final String TOPIC = "it-consumer-offsets";

    private ConnectionPool connectionPool;
    private Consumer<String, String> consumer;

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
        ConsumerConfig consumerConfig = ConsumerConfig.builder().groupId("it-consumer-group").build();
        consumer = new Consumer<>(connectionPool, config, TOPIC, consumerConfig);
        consumer.assign(Collections.singletonList(new TopicPartition(TOPIC, 0)));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (connectionPool != null) {
            connectionPool.close();
        }
    }

    @Test
    void testSeekToOffset() {
        assertDoesNotThrow(() -> consumer.seek(0, 0L));
        assertEquals(0L, consumer.position(0));
    }

    @Test
    void testPosition() {
        assertTrue(consumer.position(0) >= 0);
    }

    @Test
    void testListTopics() {
        Map<String, List<PartitionInfo>> topics = consumer.listTopics();
        assertNotNull(topics);
    }
}
