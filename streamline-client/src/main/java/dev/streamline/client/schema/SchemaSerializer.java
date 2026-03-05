package dev.streamline.client.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema-aware serializer that produces wire-format bytes compatible with
 * the Confluent Schema Registry protocol.
 *
 * <p>Wire format: {@code 0x00} (magic byte) + 4-byte big-endian schema ID + JSON payload.
 *
 * <p>Schema IDs are cached per subject so that repeated serializations for the
 * same subject avoid redundant registry calls.
 *
 * <p>Example usage:
 * <pre>{@code
 * SchemaRegistryClient registry = new SchemaRegistryClient("http://localhost:9094");
 * SchemaSerializer<MyEvent> serializer = new SchemaSerializer<>(
 *     registry, "my-topic-value", "{\"type\":\"object\"}", SchemaType.JSON
 * );
 * byte[] bytes = serializer.serialize("my-topic", event);
 * }</pre>
 *
 * @param <T> the value type to serialize
 */
public class SchemaSerializer<T> {

    private static final byte MAGIC_BYTE = 0x00;
    private static final int HEADER_SIZE = 1 + 4; // magic byte + schema ID

    private final SchemaRegistryClient registryClient;
    private final String subject;
    private final String schema;
    private final SchemaType schemaType;
    private final boolean autoRegister;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Integer> schemaIdCache;

    /**
     * Creates a serializer with auto-registration enabled.
     *
     * @param registryClient the schema registry client
     * @param subject        the subject name (e.g. {@code "my-topic-value"})
     * @param schema         the schema definition string
     * @param schemaType     the schema type
     */
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaType schemaType) {
        this(registryClient, subject, schema, schemaType, true);
    }

    /**
     * Creates a serializer with configurable auto-registration.
     *
     * @param registryClient the schema registry client
     * @param subject        the subject name (e.g. {@code "my-topic-value"})
     * @param schema         the schema definition string
     * @param schemaType     the schema type
     * @param autoRegister   whether to auto-register the schema on first use
     */
    public SchemaSerializer(SchemaRegistryClient registryClient, String subject,
                            String schema, SchemaType schemaType, boolean autoRegister) {
        this.registryClient = Objects.requireNonNull(registryClient, "registryClient must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.schemaType = Objects.requireNonNull(schemaType, "schemaType must not be null");
        this.autoRegister = autoRegister;
        this.objectMapper = new ObjectMapper();
        this.schemaIdCache = new ConcurrentHashMap<>();
    }

    /**
     * Serializes a value into the schema-registry wire format.
     *
     * @param topic the topic name
     * @param value the value to serialize
     * @return the wire-format bytes (magic byte + schema ID + JSON payload)
     * @throws StreamlineException if serialization or schema registration fails
     */
    public byte[] serialize(String topic, T value) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(value, "value must not be null");

        int schemaId = resolveSchemaId();
        byte[] payload = serializeToJson(value);

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buffer.put(MAGIC_BYTE);
        buffer.putInt(schemaId);
        buffer.put(payload);

        return buffer.array();
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
            return registryClient.register(s, schema, schemaType);
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
