package dev.streamline.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidationTest {

    @Test
    void shouldCreateWithEmptyBootstrapServers() {
        var client = Streamline.builder()
            .bootstrapServers("")
            .build();
        assertNotNull(client);
    }

    @Test
    void shouldAcceptValidConfiguration() {
        var client = Streamline.builder()
            .bootstrapServers("localhost:9092")
            .build();
        assertNotNull(client);
    }

    @Test
    void shouldUseDefaultTimeout() {
        var config = StreamlineConfig.builder()
            .bootstrapServers("localhost:9092")
            .build();
        assertEquals(30000, config.getRequestTimeoutMs());
    }
}
