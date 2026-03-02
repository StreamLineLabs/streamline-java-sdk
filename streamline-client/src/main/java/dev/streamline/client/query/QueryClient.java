package dev.streamline.client.query;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * StreamQL query client for executing SQL queries on streaming data.
 */
public class QueryClient {
    private final HttpClient httpClient;
    private final String baseUrl;

    public QueryClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
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
        String body = String.format(
            "{\"sql\":\"%s\",\"timeout_ms\":%d,\"max_rows\":%d,\"format\":\"json\"}",
            sql.replace("\"", "\\\""), timeoutMs, maxRows
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMillis(timeoutMs + 5000))
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
        String body = String.format("{\"sql\":\"%s\"}", sql.replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/query/explain"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Explain failed: " + response.body());
        }
        return response.body();
    }
}
