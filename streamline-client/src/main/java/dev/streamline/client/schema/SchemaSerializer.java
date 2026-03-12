package dev.streamline.client.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema-aware serializer that produces wire-format bytes.
 *
 * <p>Supports two wire formats (see {@link WireFormat}):
 * <ul>
 *   <li><b>STREAMLINE</b> (default):
 *       {@code [4 bytes: "SL\0\1"] [4 bytes: schema ID] [payload]}
 *   <li><b>CONFLUENT</b>:
 *       {@code [1 byte: 0x00] [4 bytes: schema ID] [payload]}
 * </ul>
 *
 * <p>Schema IDs are cached per subject so that repeated serializations
 * avoid redundant registry calls. Auto-registration can be enabled
 * (default) or disabled for stricter environments.
 *
 * <p>Example usage:
 * <pre>{@code
 * SchemaRegistryClient registry = new SchemaRegistryClient("http://localhost:9094");
 *
 * // Streamline-native format (default)
 * SchemaSerializer<MyEvent> serializer = new SchemaSerializer<>(
 *     registry, "my-topic-value", "{\"type\":\"object\"}", SchemaFormat.JSON
 * );
 *
 * // Confluent-compatible format (for Kafka interop)
 * SchemaSerializer<MyEvent> confluentSerializer = new SchemaSerializer<>(
 *     registry, "my-topic-value", "{\"type\":\"object\"}", SchemaFormat.JSON,
 *     true, WireFormat.CONFLUENT
 * );
 * byte[] bytes = serializer.serialize("my-topic", event);
 * }</pre>
 *
 * @param <T> the value type to serialize
 */
public class SchemaSerializer<T> {

    /** Streamline wire format v1 magic marker: {@code "SL\0\1"}. */
    static final byte[] MAGIC_BYTES = {0x53, 0x4C, 0x00, 0x01};

    /** Total header size for Streamline format: 4-byte magic + 4-byte schema ID. */
    static final int HEADER_SIZE = MAGIC_BYTES.length + 4;

    private final SchemaRegistryClient registryClient;
    private final String subject;
    private final String schema;
    private final SchemaFormat schemaFormat;
    private final boolean autoRegister;
    private final WireFormat wireFormat;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Integer> schemaIdCache;

    /**
     * Creates a serializer with auto-registration enabled and Streamline wire format.
     *
     * @param registryClient the schema registry client
     * @param subject        the subject name (e.g. {@code "my-topic-value"})
     * @param schema         the schema definition string
     * @param schemaFormat   the schema format
     */
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaFormat schemaFormat) {
        this(registryClient, subject, schema, schemaFormat, true, WireFormat.STREAMLINE);
    }

    /**
     * Creates a serializer with configurable auto-registration and Streamline wire format.
     *
     * @param registryClient the schema registry client
     * @param subject        the subject name (e.g. {@code "my-topic-value"})
     * @param schema         the schema definition string
     * @param schemaFormat   the schema format
     * @param autoRegister   whether to auto-register the schema on first use
     */
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaFormat schemaFormat, boolean autoRegister) {
        this(registryClient, subject, schema, schemaFormat, autoRegister, WireFormat.STREAMLINE);
    }

    /**
     * Creates a serializer with full configuration including wire format.
     *
     * @param registryClient the schema registry client
     * @param subject        the subject name (e.g. {@code "my-topic-value"})
     * @param schema         the schema definition string
     * @param schemaFormat   the schema format
     * @param autoRegister   whether to auto-register the schema on first use
     * @param wireFormat     the wire format to use ({@link WireFormat#STREAMLINE} or
     *                       {@link WireFormat#CONFLUENT})
     */
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaFormat schemaFormat, boolean autoRegister,
                            WireFormat wireFormat) {
        this.registryClient = Objects.requireNonNull(registryClient, "registryClient must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.schemaFormat = Objects.requireNonNull(schemaFormat, "schemaFormat must not be null");
        this.autoRegister = autoRegister;
        this.wireFormat = Objects.requireNonNull(wireFormat, "wireFormat must not be null");
        this.objectMapper = new ObjectMapper();
        this.schemaIdCache = new ConcurrentHashMap<>();
    }

    /**
     * Creates a serializer using the legacy {@link SchemaType} enum.
     *
     * @deprecated use the {@link SchemaFormat} constructor instead
     */
    @Deprecated
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaType schemaType) {
        this(registryClient, subject, schema, SchemaFormat.valueOf(schemaType.name()), true, WireFormat.STREAMLINE);
    }

    /**
     * Creates a serializer using the legacy {@link SchemaType} enum.
     *
     * @deprecated use the {@link SchemaFormat} constructor instead
     */
    @Deprecated
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaType schemaType, boolean autoRegister) {
        this(registryClient, subject, schema, SchemaFormat.valueOf(schemaType.name()), autoRegister, WireFormat.STREAMLINE);
    }

    /**
     * Serializes a value into the configured wire format.
     *
     * <p>The output bytes contain the format-specific magic marker, the
     * big-endian schema ID, followed by the JSON-encoded payload.
     *
     * @param topic the topic name (used for context; not encoded into the bytes)
     * @param value the value to serialize
     * @return the wire-format bytes
     * @throws StreamlineException if serialization or schema registration fails
     */
    public byte[] serialize(String topic, T value) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(value, "value must not be null");

        int schemaId = resolveSchemaId();
        byte[] payload = serializeToJson(value);

        byte[] magic = wireFormat.magic();
        int hdrSize = wireFormat.headerSize();
        ByteBuffer buffer = ByteBuffer.allocate(hdrSize + payload.length);
        buffer.put(magic);
        buffer.putInt(schemaId);
        buffer.put(payload);

        return buffer.array();
    }

    /** Returns the wire format in use. */
    public WireFormat wireFormat() {
        return wireFormat;
    }

    /**
     * Returns the cached or newly registered schema ID for this serializer's subject.
     */
    int resolveSchemaId() {
        return schemaIdCache.computeIfAbsent(subject, s -> {
            if (!autoRegister) {
                throw new StreamlineException(
                    "Schema not registered and auto-registration is disabled for subject: " + s
                );
            }
            return registryClient.registerSchema(s, schema, schemaFormat);
        });
    }

    private byte[] serializeToJson(T value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new StreamlineException("Failed to serialize value to JSON", e);
        }
    }
}
