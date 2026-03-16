package dev.streamline.spring;

import dev.streamline.client.Streamline;
import dev.streamline.client.StreamlineConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link StreamlineHealthIndicator}.
 */
class StreamlineHealthIndicatorTest {

    private final Streamline streamline = mock(Streamline.class);
    private final StreamlineConfig config = mock(StreamlineConfig.class);
    private final StreamlineHealthIndicator indicator = new StreamlineHealthIndicator(streamline);

    @Test
    void healthReportsUpWhenClientIsHealthy() {
        when(streamline.isHealthy()).thenReturn(true);
        when(streamline.getConfig()).thenReturn(config);
        when(config.getBootstrapServers()).thenReturn("localhost:9092");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "connected");
    }

    @Test
    void healthReportsDownWhenClientIsUnhealthy() {
        when(streamline.isHealthy()).thenReturn(false);
        when(streamline.getConfig()).thenReturn(config);
        when(config.getBootstrapServers()).thenReturn("localhost:9092");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "disconnected");
    }

    @Test
    void healthDetailsIncludeBootstrapServers() {
        when(streamline.isHealthy()).thenReturn(true);
        when(streamline.getConfig()).thenReturn(config);
        when(config.getBootstrapServers()).thenReturn("broker1:9092,broker2:9092");

        Health health = indicator.health();

        assertThat(health.getDetails()).containsEntry("bootstrapServers", "broker1:9092,broker2:9092");
    }
}
