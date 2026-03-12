package dev.streamline.client.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.streamline.client.StreamlineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade HTTP client for the Streamline schema registry API.
 *
 * <p>Provides schema registration, retrieval, compatibility checking, and
 * subject management against the Streamline HTTP API (default port 9094).
 * Uses Jackson for JSON serialization and caches schemas in memory to
 * minimize redundant network calls.
 *
 * <p>This client is thread-safe and designed for reuse across threads.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (SchemaRegistryClient client = new SchemaRegistryClient("http://localhost:9094")) {
 *     int id = client.registerSchema("orders-value", schema, SchemaFormat.JSON);
 *     Schema latest = client.getLatestSchema("orders-value");
 *     boolean ok = client.checkCompatibility("orders-value", newSchema);
 * }
 * }</pre>
 */
public class SchemaRegistryClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistryClient.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:9094";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String CONTENT_TYPE = "application/json";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final ConcurrentHashMap<String, Schema> schemaBySvCache;
    private final ConcurrentHashMap<Integer, Schema> schemaByIdCache;

    /**
     * Creates a client with the default base URL ({@code http://localhost:9094}).
     */
    public SchemaRegistryClient() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Creates a client with the specified base URL and default timeout.
     *
     * @param baseUrl the schema registry base URL (e.g. {@code http://localhost:9094})
     */
    public SchemaRegistryClient(String baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a client with the specified base URL and request timeout.
     *
     * @param baseUrl        the schema registry base URL
     * @param requestTimeout the HTTP request timeout
     */
    public SchemaRegistryClient(String baseUrl, Duration requestTimeout) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(requestTimeout)
            .build();
        this.objectMapper = new ObjectMapper();
        this.schemaBySvCache = new ConcurrentHashMap<>();
        this.schemaByIdCache = new ConcurrentHashMap<>();
    }

    // ── Schema Registration ──────────────────────────────────────────────

    /**
     * Registers a schema under the given subject.
     *
     * <p>If the schema already exists for this subject, the existing ID is returned
     * without creating a new version.
     *
     * @param subject the subject name (e.g. {@code "orders-value"})
     * @param schema  the schema definition string
     * @param format  the schema format
     * @return the globally unique schema ID
     * @throws StreamlineException if registration fails
     */
    public int registerSchema(String subject, String schema, SchemaFormat format) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(format, "format must not be null");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("schema", schema);
        body.put("schemaType", format.name());

        log.debug("Registering schema for subject '{}' with format {}", subject, format);

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/subjects/" + subject + "/versions"))
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .timeout(requestTimeout)
            .build());

        JsonNode response = parseJson(responseBody);
        int id = response.get("id").asInt();
        log.debug("Registered schema for subject '{}': id={}", subject, id);
        return id;
    }

    /**
     * Registers a schema under the given subject.
     *
     * @param subject the subject name
     * @param schema  the schema definition
     * @param type    the schema type
     * @return the registered schema ID
     * @deprecated use {@link #registerSchema(String, String, SchemaFormat)} instead
     */
    @Deprecated
    public int register(String subject, String schema, SchemaType type) {
        return registerSchema(subject, schema, SchemaFormat.valueOf(type.name()));
    }

    // ── Schema Retrieval ─────────────────────────────────────────────────

    /**
     * Retrieves a schema for the given subject and version.
     *
     * <p>Results are cached by subject+version. Subsequent calls with the same
     * arguments return the cached {@link Schema} without a network round-trip.
     *
     * @param subject the subject name
     * @param version the version number (1-based)
     * @return the schema
     * @throws StreamlineException if the subject/version is not found
     */
    public Schema getSchema(String subject, int version) {
        Objects.requireNonNull(subject, "subject must not be null");

        String cacheKey = subject + ":" + version;
        Schema cached = schemaBySvCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/subjects/" + subject + "/versions/" + version))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        Schema schema = parseSchemaResponse(responseBody);
        schemaBySvCache.put(cacheKey, schema);
        schemaByIdCache.put(schema.id(), schema);
        return schema;
    }

    /**
     * Retrieves a schema by its global ID.
     *
     * <p>Returns the raw schema definition string. Results are cached by ID.
     *
     * @param id the global schema ID
     * @return the schema definition string
     * @throws StreamlineException if the schema ID is not found
     */
    public String getSchema(int id) {
        Schema cached = schemaByIdCache.get(id);
        if (cached != null) {
            return cached.schema();
        }

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/schemas/ids/" + id))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        JsonNode response = parseJson(responseBody);
        String schemaStr = response.get("schema").asText();
        SchemaFormat format = parseSchemaFormat(response);

        Schema schema = new Schema(id, null, 0, format, schemaStr, List.of());
        schemaByIdCache.put(id, schema);
        return schemaStr;
    }

    /**
     * Retrieves the latest schema version for the given subject.
     *
     * <p>The result is cached by subject+version and by global ID.
     *
     * @param subject the subject name
     * @return the latest schema version
     * @throws StreamlineException if the subject is not found
     */
    public Schema getLatestSchema(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/subjects/" + subject + "/versions/latest"))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        Schema schema = parseSchemaResponse(responseBody);
        schemaBySvCache.put(subject + ":" + schema.version(), schema);
        schemaByIdCache.put(schema.id(), schema);
        return schema;
    }

    /**
     * Lists all version numbers registered for a subject.
     *
     * @param subject the subject name
     * @return the list of version numbers
     * @throws StreamlineException if the subject is not found
     */
    public List<Integer> getVersions(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/subjects/" + subject + "/versions"))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        try {
            return objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new StreamlineException("Failed to parse versions list", e);
        }
    }

    // ── Subject Management ───────────────────────────────────────────────

    /**
     * Lists all subjects registered in the schema registry.
     *
     * @return the list of subject names
     * @throws StreamlineException if the request fails
     */
    public List<String> listSubjects() {
        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/subjects"))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        try {
            return objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new StreamlineException("Failed to parse subjects list", e);
        }
    }

    /**
     * Deletes a subject and all its schema versions from the registry.
     *
     * <p>Cache entries for the deleted subject are invalidated.
     *
     * @param subject the subject to delete
     * @throws StreamlineException if deletion fails
     */
    public void deleteSubject(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");

        log.debug("Deleting subject '{}'", subject);

        execute(HttpRequest.newBuilder()
            .uri(uri("/subjects/" + subject))
            .DELETE()
            .timeout(requestTimeout)
            .build());

        schemaBySvCache.entrySet().removeIf(e -> e.getKey().startsWith(subject + ":"));
        log.debug("Deleted subject '{}' and invalidated cache", subject);
    }

    // ── Compatibility ────────────────────────────────────────────────────

    /**
     * Checks whether a schema is compatible with the latest version of a subject.
     *
     * @param subject the subject name
     * @param schema  the schema to check
     * @return {@code true} if the schema is compatible
     * @throws StreamlineException if the compatibility check fails
     */
    public boolean checkCompatibility(String subject, String schema) {
        return checkCompatibility(subject, schema, SchemaFormat.AVRO);
    }

    /**
     * Checks whether a schema is compatible with the latest version of a subject.
     *
     * @param subject the subject name
     * @param schema  the schema to check
     * @param format  the schema format
     * @return {@code true} if the schema is compatible
     * @throws StreamlineException if the compatibility check fails
     */
    public boolean checkCompatibility(String subject, String schema, SchemaFormat format) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(format, "format must not be null");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("schema", schema);
        body.put("schemaType", format.name());

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/compatibility/subjects/" + subject + "/versions/latest"))
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .timeout(requestTimeout)
            .build());

        JsonNode response = parseJson(responseBody);
        return response.has("is_compatible") && response.get("is_compatible").asBoolean();
    }

    /**
     * Checks whether a schema is compatible with the latest version of a subject.
     *
     * @param subject the subject name
     * @param schema  the schema to check
     * @param type    the schema type
     * @return {@code true} if the schema is compatible
     * @deprecated use {@link #checkCompatibility(String, String, SchemaFormat)} instead
     */
    @Deprecated
    public boolean checkCompatibility(String subject, String schema, SchemaType type) {
        return checkCompatibility(subject, schema, SchemaFormat.valueOf(type.name()));
    }

    // ── Compatibility Configuration ──────────────────────────────────────

    /**
     * Retrieves the compatibility level configured for a subject.
     *
     * @param subject the subject name
     * @return the compatibility level
     * @throws StreamlineException if the subject is not found or the request fails
     */
    public CompatibilityLevel getCompatibilityLevel(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");

        String responseBody = execute(HttpRequest.newBuilder()
            .uri(uri("/config/" + subject))
            .header("Accept", CONTENT_TYPE)
            .GET()
            .timeout(requestTimeout)
            .build());

        JsonNode response = parseJson(responseBody);
        String level = response.get("compatibilityLevel").asText();
        return CompatibilityLevel.valueOf(level);
    }

    /**
     * Sets the compatibility level for a subject.
     *
     * @param subject the subject name
     * @param level   the compatibility level to set
     * @throws StreamlineException if the request fails
     */
    public void setCompatibilityLevel(String subject, CompatibilityLevel level) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(level, "level must not be null");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("compatibility", level.name());

        log.debug("Setting compatibility for subject '{}' to {}", subject, level);

        execute(HttpRequest.newBuilder()
            .uri(uri("/config/" + subject))
            .header("Content-Type", CONTENT_TYPE)
            .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .timeout(requestTimeout)
            .build());
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears internal caches and releases resources.
     */
    @Override
    public void close() {
        schemaBySvCache.clear();
        schemaByIdCache.clear();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    /**
     * Executes an HTTP request and returns the response body.
     *
     * <p>Subclasses (e.g. test stubs) may override individual public methods
     * rather than this low-level transport method.
     */
    protected String execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                handleErrorResponse(response);
            }
            return response.body();
        } catch (StreamlineException e) {
            throw e;
        } catch (IOException e) {
            throw StreamlineException.connectionFailed(baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Schema registry request interrupted", e);
        }
    }

    private void handleErrorResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        String message;
        int errorCode = status;
        try {
            JsonNode error = objectMapper.readTree(body);
            message = error.has("message") ? error.get("message").asText() : body;
            if (error.has("error_code")) {
                errorCode = error.get("error_code").asInt();
            }
        } catch (JsonProcessingException e) {
            message = body;
        }

        if (status == 404) {
            throw new StreamlineException(
                "Schema registry resource not found: " + message,
                "SCHEMA_NOT_FOUND"
            );
        }
        if (status == 409) {
            throw new StreamlineException(
                "Schema compatibility check failed: " + message,
                "SCHEMA_INCOMPATIBLE"
            );
        }
        throw new StreamlineException(
            "Schema registry error (HTTP " + status + ", error_code " + errorCode + "): " + message
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new StreamlineException("Failed to parse schema registry response", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new StreamlineException("Failed to serialize request body", e);
        }
    }

    private Schema parseSchemaResponse(String json) {
        JsonNode node = parseJson(json);

        int id = node.has("id") ? node.get("id").asInt() : 0;
        String subject = node.has("subject") ? node.get("subject").asText() : null;
        int version = node.has("version") ? node.get("version").asInt() : 0;
        String schemaStr = node.get("schema").asText();
        SchemaFormat format = parseSchemaFormat(node);

        List<Schema.SchemaReference> refs = new ArrayList<>();
        if (node.has("references") && node.get("references").isArray()) {
            for (JsonNode ref : node.get("references")) {
                refs.add(new Schema.SchemaReference(
                    ref.get("name").asText(),
                    ref.get("subject").asText(),
                    ref.get("version").asInt()
                ));
            }
        }

        return new Schema(id, subject, version, format, schemaStr,
            Collections.unmodifiableList(refs));
    }

    private static SchemaFormat parseSchemaFormat(JsonNode node) {
        if (node.has("schemaType")) {
            try {
                return SchemaFormat.valueOf(node.get("schemaType").asText());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return SchemaFormat.AVRO;
    }
}
