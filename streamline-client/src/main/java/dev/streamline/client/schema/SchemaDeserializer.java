package dev.streamline.client.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema-aware deserializer that reads wire-format bytes produced by
 * {@link SchemaSerializer}.
 *
 * <p>Supports both Streamline and Confluent wire formats. The format is
 * automatically detected from the first byte of the data:
 * <ul>
 *   <li>{@code 0x53} → Streamline format (8-byte header)
 *   <li>{@code 0x00} → Confluent format (5-byte header)
 * </ul>
 *
 * <p>The schema ID extracted from the header is used to look up the schema
 * string from the registry. Schema strings are cached by ID to avoid
 * redundant registry lookups.
 *
 * <p>Example usage:
 * <pre>{@code
 * SchemaRegistryClient registry = new SchemaRegistryClient("http://localhost:9094");
 * SchemaDeserializer<MyEvent> deserializer = new SchemaDeserializer<>(registry);
 * // Works with both Streamline and Confluent wire format automatically:
 * DeserializedRecord<MyEvent> record = deserializer.deserialize(bytes, MyEvent.class);
 * System.out.println("Schema ID: " + record.schemaId());
 * System.out.println("Value: " + record.value());
 * }</pre>
 *
 * @param <T> the target value type
 */
public class SchemaDeserializer<T> {

    /** Expected magic bytes — must match {@link SchemaSerializer#MAGIC_BYTES}. */
    static final byte[] MAGIC_BYTES = SchemaSerializer.MAGIC_BYTES;

    /** Total header size for Streamline format: 4-byte magic + 4-byte schema ID. */
    static final int HEADER_SIZE = SchemaSerializer.HEADER_SIZE;

    private final SchemaRegistryClient registryClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Integer, String> schemaCache;

    /**
     * Creates a deserializer backed by the given schema registry client.
     *
     * @param registryClient the schema registry client
     */
    public SchemaDeserializer(SchemaRegistryClient registryClient) {
        this.registryClient = Objects.requireNonNull(registryClient, "registryClient must not be null");
        this.objectMapper = new ObjectMapper();
        this.schemaCache = new ConcurrentHashMap<>();
    }

    /**
     * Deserializes wire-format bytes into a typed record with schema metadata.
     *
     * <p>Automatically detects whether the data uses Streamline or Confluent
     * wire format based on the first byte.
     *
     * @param data        the wire-format bytes (magic + schema ID + JSON payload)
     * @param targetClass the class to deserialize the JSON payload into
     * @return a {@link DeserializedRecord} containing the schema ID, schema string, and value
     * @throws StreamlineException if the data is malformed or deserialization fails
     */
    public DeserializedRecord<T> deserialize(byte[] data, Class<T> targetClass) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");

        WireFormat format;
        try {
            format = WireFormat.detect(data);
        } catch (IllegalArgumentException e) {
            throw new StreamlineException("Unrecognized wire format: " + e.getMessage());
        }

        int hdrSize = format.headerSize();
        if (data.length < hdrSize) {
            throw new StreamlineException(
                "Invalid " + format + " wire format: expected at least " + hdrSize
                    + " bytes, got " + data.length
            );
        }

        byte[] magic = format.magic();
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                throw new StreamlineException(
                    "Invalid magic bytes for " + format + " wire format"
                );
            }
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, magic.length, 4);
        int schemaId = buffer.getInt();
        String schema = resolveSchema(schemaId);

        byte[] payload = Arrays.copyOfRange(data, hdrSize, data.length);
        T value = deserializeFromJson(payload, targetClass);
        return new DeserializedRecord<>(schemaId, schema, value);
    }

    /**
     * Returns the cached or freshly fetched schema string for the given ID.
     */
    String resolveSchema(int schemaId) {
        return schemaCache.computeIfAbsent(schemaId, registryClient::getSchema);
    }

    private T deserializeFromJson(byte[] payload, Class<T> targetClass) {
        try {
            return objectMapper.readValue(payload, targetClass);
        } catch (IOException e) {
            throw new StreamlineException("Failed to deserialize JSON payload", e);
        }
    }

    private static String formatBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("0x%02x", bytes[i]));
        }
        return sb.toString();
    }
}
