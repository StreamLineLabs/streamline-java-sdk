package dev.streamline.testsupport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Resolves the environment used by the integration tests ({@code *IT}) and enforces
 * an explicit, deterministic opt-in.
 *
 * <p>Selection rules — {@link #requireAvailable()}:
 * <ol>
 *   <li>{@code STREAMLINE_INTEGRATION} is not {@code 1} → the test is <b>skipped</b>
 *       with a message explaining how to enable it. Integration tests never run
 *       implicitly, so {@code mvn verify} stays self-contained.</li>
 *   <li>{@code STREAMLINE_INTEGRATION=1} but an endpoint is unreachable → the test
 *       <b>fails fast</b> (within {@link #PROBE_TIMEOUT} per endpoint). Once
 *       integration testing is explicitly requested, a missing server is an error,
 *       never a silent skip.</li>
 *   <li>{@code STREAMLINE_INTEGRATION=1} and endpoints reachable → the test runs.</li>
 * </ol>
 *
 * <p>Endpoints are configurable so the suite can run against a container, a
 * {@code docker compose} service, or a remote cluster:
 * <table border="1">
 *   <caption>Environment variables</caption>
 *   <tr><th>Variable</th><th>Default</th></tr>
 *   <tr><td>{@code STREAMLINE_BOOTSTRAP_SERVERS} (or {@code STREAMLINE_BOOTSTRAP})</td>
 *       <td>{@value #DEFAULT_BOOTSTRAP_SERVERS}</td></tr>
 *   <tr><td>{@code STREAMLINE_HTTP_URL} (or {@code STREAMLINE_HTTP})</td>
 *       <td>{@value #DEFAULT_HTTP_URL}</td></tr>
 *   <tr><td>{@code STREAMLINE_SCHEMA_REGISTRY_URL}</td>
 *       <td>the resolved HTTP URL</td></tr>
 * </table>
 */
public final class IntegrationEnvironment {

    /** Opt-in switch; integration tests only run when this is exactly {@code 1}. */
    public static final String ENABLED_VAR = "STREAMLINE_INTEGRATION";
    /** Preferred bootstrap server variable. */
    public static final String BOOTSTRAP_VAR = "STREAMLINE_BOOTSTRAP_SERVERS";
    /** Legacy alias for {@link #BOOTSTRAP_VAR}, kept for existing CI definitions. */
    public static final String BOOTSTRAP_VAR_ALIAS = "STREAMLINE_BOOTSTRAP";
    /** Preferred HTTP API variable. */
    public static final String HTTP_VAR = "STREAMLINE_HTTP_URL";
    /** Legacy alias for {@link #HTTP_VAR}, kept for existing CI definitions. */
    public static final String HTTP_VAR_ALIAS = "STREAMLINE_HTTP";
    /** Schema Registry endpoint; defaults to the HTTP API endpoint. */
    public static final String SCHEMA_REGISTRY_VAR = "STREAMLINE_SCHEMA_REGISTRY_URL";

    /** Default Kafka-protocol endpoint. */
    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    /** Default HTTP API endpoint. */
    public static final String DEFAULT_HTTP_URL = "http://localhost:9094";

    /** Upper bound for a single reachability probe. */
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private static final String HOW_TO_ENABLE =
            "Integration tests are disabled. Enable them with:\n"
                    + "  docker compose -f docker-compose.test.yml up -d\n"
                    + "  STREAMLINE_INTEGRATION=1 mvn verify -Pintegration\n"
                    + "Override endpoints with " + BOOTSTRAP_VAR + " / " + HTTP_VAR + ".";

    private IntegrationEnvironment() {
    }

    /** @return {@code true} when {@code STREAMLINE_INTEGRATION=1} is exported. */
    public static boolean isEnabled() {
        return isEnabled(System.getenv());
    }

    static boolean isEnabled(Map<String, String> env) {
        return "1".equals(trimmedOrNull(env.get(ENABLED_VAR)));
    }

    /** @return the configured bootstrap servers, or {@value #DEFAULT_BOOTSTRAP_SERVERS}. */
    public static String bootstrapServers() {
        return bootstrapServers(System.getenv());
    }

    static String bootstrapServers(Map<String, String> env) {
        return firstNonBlank(
                env.get(BOOTSTRAP_VAR), env.get(BOOTSTRAP_VAR_ALIAS), DEFAULT_BOOTSTRAP_SERVERS);
    }

    /** @return the configured HTTP API base URL, or {@value #DEFAULT_HTTP_URL}. */
    public static String httpUrl() {
        return httpUrl(System.getenv());
    }

    static String httpUrl(Map<String, String> env) {
        return stripTrailingSlash(
                firstNonBlank(env.get(HTTP_VAR), env.get(HTTP_VAR_ALIAS), DEFAULT_HTTP_URL));
    }

    /** @return the configured Schema Registry base URL; defaults to {@link #httpUrl()}. */
    public static String schemaRegistryUrl() {
        return schemaRegistryUrl(System.getenv());
    }

    static String schemaRegistryUrl(Map<String, String> env) {
        return stripTrailingSlash(firstNonBlank(env.get(SCHEMA_REGISTRY_VAR), httpUrl(env)));
    }

    /**
     * Skips the calling test when integration testing was not explicitly enabled, and
     * fails fast when it was enabled but the server is unreachable.
     */
    public static void requireAvailable() {
        Assumptions.assumeTrue(isEnabled(), HOW_TO_ENABLE);
        requireReachable(bootstrapServers(), BOOTSTRAP_VAR);
        requireReachable(hostPort(httpUrl()), HTTP_VAR);
    }

    /**
     * Fails fast when the Schema Registry endpoint is unreachable. Call from tests that
     * additionally need the HTTP Schema Registry; {@link #requireAvailable()} must have
     * been called first.
     */
    public static void requireSchemaRegistry() {
        requireReachable(hostPort(schemaRegistryUrl()), SCHEMA_REGISTRY_VAR);
    }

    private static void requireReachable(String hostPortList, String variable) {
        String firstEndpoint = hostPortList.split(",")[0].trim();
        if (isReachable(firstEndpoint, PROBE_TIMEOUT)) {
            return;
        }
        Assertions.fail(ENABLED_VAR + "=1 but " + variable + " endpoint '" + firstEndpoint
                + "' is not reachable within " + PROBE_TIMEOUT.toSeconds() + "s.\n"
                + "Start a server (docker compose -f docker-compose.test.yml up -d) or point "
                + variable + " at a running instance.");
    }

    /** @return {@code true} when a TCP connection to {@code host:port} succeeds in time. */
    public static boolean isReachable(String hostPort, Duration timeout) {
        int separator = hostPort.lastIndexOf(':');
        if (separator < 0) {
            return false;
        }
        String host = hostPort.substring(0, separator);
        int port;
        try {
            port = Integer.parseInt(hostPort.substring(separator + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    /** @return {@code host:port} for a URL, applying the scheme's default port. */
    static String hostPort(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        return host + ":" + port;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimmedOrNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        throw new IllegalArgumentException("No non-blank value supplied");
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
