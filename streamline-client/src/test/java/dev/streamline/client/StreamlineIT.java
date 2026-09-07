package dev.streamline.client;

import dev.streamline.testsupport.IntegrationEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Streamline} facade tests that need a live broker.
 *
 * <p>Run with {@code STREAMLINE_INTEGRATION=1 mvn verify -Pintegration}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = IntegrationEnvironment.ENABLED_VAR, matches = "1",
        disabledReason = "set STREAMLINE_INTEGRATION=1 and run with -Pintegration")
class StreamlineIT {

    private Streamline client;

    @BeforeEach
    void setUp() {
        IntegrationEnvironment.requireAvailable();
        client = Streamline.builder()
                .bootstrapServers(IntegrationEnvironment.bootstrapServers())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testProduce() {
        RecordMetadata metadata = client.produce("it-streamline-produce", "key1", "value1");

        assertNotNull(metadata);
        assertEquals("it-streamline-produce", metadata.topic());
        assertTrue(metadata.offset() >= 0);
        assertTrue(metadata.timestamp() > 0);
    }

    @Test
    void testProduceAsync() throws Exception {
        CompletableFuture<RecordMetadata> future =
                client.produceAsync("it-streamline-produce-async", "key1", "value1");

        assertNotNull(future);
        RecordMetadata metadata = future.get(30, TimeUnit.SECONDS);
        assertNotNull(metadata);
        assertEquals("it-streamline-produce-async", metadata.topic());
    }
}
