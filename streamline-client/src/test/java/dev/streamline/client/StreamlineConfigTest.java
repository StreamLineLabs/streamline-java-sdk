package dev.streamline.client;

import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamlineConfigTest {

    @Test
    void testRecordComponents() {
        ProducerConfig producerConfig = ProducerConfig.defaults();
        ConsumerConfig consumerConfig = ConsumerConfig.defaults();

        StreamlineConfig config = new StreamlineConfig(
            "localhost:9092", producerConfig, consumerConfig, 4, 30000, 30000
        );

        assertEquals("localhost:9092", config.bootstrapServers());
        assertSame(producerConfig, config.producerConfig());
        assertSame(consumerConfig, config.consumerConfig());
        assertEquals(4, config.connectionPoolSize());
        assertEquals(30000, config.connectTimeoutMs());
        assertEquals(30000, config.requestTimeoutMs());
    }

    @Test
    void testGetterMethods() {
        ProducerConfig producerConfig = ProducerConfig.defaults();
        ConsumerConfig consumerConfig = ConsumerConfig.defaults();

        StreamlineConfig config = new StreamlineConfig(
            "host1:9092,host2:9092", producerConfig, consumerConfig, 8, 10000, 5000
        );

        assertEquals("host1:9092,host2:9092", config.getBootstrapServers());
        assertSame(producerConfig, config.getProducerConfig());
        assertSame(consumerConfig, config.getConsumerConfig());
        assertEquals(8, config.getConnectionPoolSize());
        assertEquals(10000, config.getConnectTimeoutMs());
        assertEquals(5000, config.getRequestTimeoutMs());
    }

    @Test
    void testEquality() {
        ProducerConfig producerConfig = ProducerConfig.defaults();
        ConsumerConfig consumerConfig = ConsumerConfig.defaults();

        StreamlineConfig config1 = new StreamlineConfig(
            "localhost:9092", producerConfig, consumerConfig, 4, 30000, 30000
        );
        StreamlineConfig config2 = new StreamlineConfig(
            "localhost:9092", producerConfig, consumerConfig, 4, 30000, 30000
        );
        StreamlineConfig config3 = new StreamlineConfig(
            "other:9092", producerConfig, consumerConfig, 4, 30000, 30000
        );

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
    }
}
