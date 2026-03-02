package dev.streamline.client.schema;

import dev.streamline.client.StreamlineException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client for interacting with the Streamline schema registry API.
 *
 * <p>Connects to the Streamline HTTP API (default port 9094) for schema
 * registration, retrieval, and compatibility checking.
 */
public class SchemaRegistryClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:9094";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String CONTENT_TYPE = "application/json";

    private final String baseUrl;
    private final HttpClient httpClient;

    /**
     * Creates a client with the default base URL ({@code http://localhost:9094}).
     */
    public SchemaRegistryClient() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Creates a client with the specified base URL.
     *
     * @param baseUrl the schema registry base URL (e.g. {@code http://localhost:9094})
     */
    public SchemaRegistryClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();
    }

    /**
     * Registers a schema for the given subject.
     *
     * @param subject the subject name
     * @param schema  the schema definition
     * @param type    the schema type
     * @return the registered schema ID
     */
    public int register(String subject, String schema, SchemaType type) {
        String body = String.format(
            "{\"schema\":%s,\"schemaType\":\"%s\"}",
            escapeJsonString(schema), type.name()
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/subjects/" + subject + "/versions"))
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(DEFAULT_TIMEOUT)
            .build();

        String response = execute(request);
        return parseId(response);
    }

    /**
     * Retrieves a schema by its global ID.
     *
     * @param id the schema ID
     * @return the schema definition
     */
    public String getSchema(int id) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/schemas/ids/" + id))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(DEFAULT_TIMEOUT)
            .build();

        String response = execute(request);
        return parseSchema(response);
    }

    /**
     * Lists all version numbers for a subject.
     *
     * @param subject the subject name
     * @return list of version numbers
     */
    public List<Integer> getVersions(String subject) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/subjects/" + subject + "/versions"))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(DEFAULT_TIMEOUT)
            .build();

        String response = execute(request);
        return parseIntArray(response);
    }

    /**
     * Checks whether a schema is compatible with the latest version of a subject.
     *
     * @param subject the subject name
     * @param schema  the schema definition to check
     * @param type    the schema type
     * @return {@code true} if the schema is compatible
     */
    public boolean checkCompatibility(String subject, String schema, SchemaType type) {
        String body = String.format(
            "{\"schema\":%s,\"schemaType\":\"%s\"}",
            escapeJsonString(schema), type.name()
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/compatibility/subjects/" + subject + "/versions/latest"))
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(DEFAULT_TIMEOUT)
            .build();

        String response = execute(request);
        return response.contains("\"is_compatible\":true") || response.contains("\"is_compatible\": true");
    }

    private String execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new StreamlineException(
                    "Schema registry request failed with status " + response.statusCode() + ": " + response.body()
                );
            }
            return response.body();
        } catch (IOException e) {
            throw new StreamlineException("Schema registry request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Schema registry request interrupted", e);
        }
    }

    private static int parseId(String json) {
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new StreamlineException("Unable to parse schema ID from response: " + json);
    }

    private static String parseSchema(String json) {
        Matcher matcher = Pattern.compile("\"schema\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        throw new StreamlineException("Unable to parse schema from response: " + json);
    }

    private static List<Integer> parseIntArray(String json) {
        List<Integer> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(json);
        while (matcher.find()) {
            result.add(Integer.parseInt(matcher.group()));
        }
        return result;
    }

    private static String escapeJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
