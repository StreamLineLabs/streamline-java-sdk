package dev.streamline.client.query;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * StreamQL query client for executing SQL queries on streaming data.
 *
 * <p>All requests are bounded: the connection attempt is capped by
 * {@link #CONNECT_TIMEOUT} and every request carries its own read timeout.
 */
public class QueryClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Upper bound for establishing the TCP/TLS connection. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Headroom added to the server-side query timeout for the client-side bound. */
    private static final Duration RESPONSE_HEADROOM = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String baseUrl;

    public QueryClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null").replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Execute a SQL query and return results as JSON string.
     */
    public String query(String sql) throws Exception {
        return query(sql, 30000, 10000);
    }

    /**
     * Execute a SQL query with timeout and row limit.
     */
    public String query(String sql, long timeoutMs, int maxRows) throws Exception {
        Objects.requireNonNull(sql, "sql must not be null");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be greater than zero");
        }
        byte[] body = JSON.writeValueAsBytes(Map.of(
                "sql", sql,
                "timeout_ms", timeoutMs,
                "max_rows", maxRows,
                "format", "json"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofMillis(timeoutMs).plus(RESPONSE_HEADROOM))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Query failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        return response.body();
    }

    /**
     * Explain a query plan.
     */
    public String explain(String sql) throws Exception {
        Objects.requireNonNull(sql, "sql must not be null");
        byte[] body = JSON.writeValueAsBytes(Map.of("sql", sql));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/query/explain"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(CONNECT_TIMEOUT.plus(RESPONSE_HEADROOM))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Explain failed: " + response.body());
        }
        return response.body();
    }
}
