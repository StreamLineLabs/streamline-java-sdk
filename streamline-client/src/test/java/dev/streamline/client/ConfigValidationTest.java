package dev.streamline.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidationTest {

    @Test
    void shouldRejectEmptyBootstrapServers() {
        assertThrows(IllegalArgumentException.class, () -> {
            StreamlineClient.builder()
                .bootstrapServers("")
                .build();
        });
    }

    @Test
    void shouldRejectNullClientId() {
        assertThrows(NullPointerException.class, () -> {
            StreamlineClient.builder()
                .bootstrapServers("localhost:9092")
                .clientId(null)
                .build();
        });
    }

    @Test
    void shouldAcceptValidConfiguration() {
        var client = StreamlineClient.builder()
            .bootstrapServers("localhost:9092")
            .clientId("test-client")
            .build();
        assertNotNull(client);
    }

    @Test
    void shouldUseDefaultTimeout() {
        var config = StreamlineClientConfig.builder()
            .bootstrapServers("localhost:9092")
            .build();
        assertEquals(30000, config.getRequestTimeoutMs());
    }
}
