package dev.streamline.spring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StreamlineMetrics}.
 */
class StreamlineMetricsTest {

    private SimpleMeterRegistry registry;
    private StreamlineMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new StreamlineMetrics(registry);
    }

    @Test
    void recordProduceIncrementsCounter() {
        metrics.recordProduce(Duration.ofMillis(10));
        metrics.recordProduce(Duration.ofMillis(20));

        assertThat(registry.counter("streamline.messages.produced").count()).isEqualTo(2.0);
        assertThat(registry.timer("streamline.produce.latency").count()).isEqualTo(2);
    }

    @Test
    void recordConsumeIncrementsCounter() {
        metrics.recordConsume(Duration.ofMillis(5));
        metrics.recordConsume(Duration.ofMillis(15));
        metrics.recordConsume(Duration.ofMillis(25));

        assertThat(registry.counter("streamline.messages.consumed").count()).isEqualTo(3.0);
        assertThat(registry.timer("streamline.consume.latency").count()).isEqualTo(3);
    }

    @Test
    void recordErrorIncrementsCounter() {
        metrics.recordError();
        metrics.recordError();

        assertThat(registry.counter("streamline.errors").count()).isEqualTo(2.0);
    }
}
