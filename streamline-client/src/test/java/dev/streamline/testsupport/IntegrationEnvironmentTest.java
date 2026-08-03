package dev.streamline.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the test-selection contract: integration tests must be opt-in and their
 * endpoints must be configurable with documented defaults.
 */
class IntegrationEnvironmentTest {

    @Test
    void integrationIsDisabledWhenVariableIsAbsent() {
        assertFalse(IntegrationEnvironment.isEnabled(Map.of()));
    }

    @Test
    void integrationIsDisabledForAnyValueOtherThanOne() {
        assertFalse(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", "0")));
        assertFalse(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", "")));
        assertFalse(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", "true")));
        assertFalse(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", "yes")));
    }

    @Test
    void integrationIsEnabledOnlyByExplicitOne() {
        assertTrue(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", "1")));
        assertTrue(IntegrationEnvironment.isEnabled(Map.of("STREAMLINE_INTEGRATION", " 1 ")));
    }

    @Test
    void bootstrapServersFallsBackToDocumentedDefault() {
        assertEquals("localhost:9092", IntegrationEnvironment.bootstrapServers(Map.of()));
        assertEquals(IntegrationEnvironment.DEFAULT_BOOTSTRAP_SERVERS,
                IntegrationEnvironment.bootstrapServers(Map.of("STREAMLINE_BOOTSTRAP_SERVERS", "  ")));
    }

    @Test
    void bootstrapServersPrefersCanonicalVariableOverAlias() {
        Map<String, String> env = Map.of(
                "STREAMLINE_BOOTSTRAP_SERVERS", "primary:9092",
                "STREAMLINE_BOOTSTRAP", "alias:9092");

        assertEquals("primary:9092", IntegrationEnvironment.bootstrapServers(env));
    }

    @Test
    void bootstrapServersHonoursLegacyAlias() {
        assertEquals("alias:9092",
                IntegrationEnvironment.bootstrapServers(Map.of("STREAMLINE_BOOTSTRAP", "alias:9092")));
    }

    @Test
    void httpUrlFallsBackToDocumentedDefault() {
        assertEquals("http://localhost:9094", IntegrationEnvironment.httpUrl(Map.of()));
    }

    @Test
    void httpUrlHonoursOverridesAndStripsTrailingSlash() {
        assertEquals("http://broker:19094",
                IntegrationEnvironment.httpUrl(Map.of("STREAMLINE_HTTP_URL", "http://broker:19094/")));
        assertEquals("http://legacy:9094",
                IntegrationEnvironment.httpUrl(Map.of("STREAMLINE_HTTP", "http://legacy:9094")));
    }

    @Test
    void schemaRegistryDefaultsToHttpEndpoint() {
        assertEquals("http://localhost:9094", IntegrationEnvironment.schemaRegistryUrl(Map.of()));
        assertEquals("http://broker:9094", IntegrationEnvironment.schemaRegistryUrl(
                Map.of("STREAMLINE_HTTP_URL", "http://broker:9094")));
    }

    @Test
    void schemaRegistryCanBeHostedSeparately() {
        Map<String, String> env = Map.of(
                "STREAMLINE_HTTP_URL", "http://broker:9094",
                "STREAMLINE_SCHEMA_REGISTRY_URL", "http://registry:8081/");

        assertEquals("http://registry:8081", IntegrationEnvironment.schemaRegistryUrl(env));
    }

    @Test
    void hostPortAppliesSchemeDefaultPort() {
        assertEquals("registry:8081", IntegrationEnvironment.hostPort("http://registry:8081"));
        assertEquals("registry:80", IntegrationEnvironment.hostPort("http://registry"));
        assertEquals("registry:443", IntegrationEnvironment.hostPort("https://registry"));
        assertThrows(IllegalArgumentException.class, () -> IntegrationEnvironment.hostPort("not-a-url"));
    }

    @Test
    void reachabilityProbeIsBoundedAndRejectsMalformedEndpoints() {
        assertFalse(IntegrationEnvironment.isReachable("no-port", Duration.ofMillis(50)));
        assertFalse(IntegrationEnvironment.isReachable("host:not-a-number", Duration.ofMillis(50)));
    }

    @Test
    void reachabilityProbeReportsFalseForUnroutableEndpoint() {
        long startNanos = System.nanoTime();
        assertFalse(IntegrationEnvironment.isReachable(
                UnitTestEndpoints.BOOTSTRAP_SERVERS, Duration.ofMillis(200)));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertTrue(elapsedMillis < 5_000,
                "probe must be bounded by its timeout, took " + elapsedMillis + "ms");
    }

    @Test
    void reachabilityProbeDetectsListeningSocket() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getLoopbackAddress())) {
            String endpoint = socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort();

            assertTrue(IntegrationEnvironment.isReachable(endpoint, Duration.ofSeconds(2)));
        }
    }
}
