package dev.streamline.client.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema-aware deserializer that reads wire-format bytes produced by
 * {@link SchemaSerializer}.
 *
 * <p>Expects wire format: {@code 0x00} (magic byte) + 4-byte big-endian
 * schema ID + JSON payload. The schema ID is used to look up the schema
 * string from the registry.
 *
 * <p>Schema strings are cached by ID to avoid redundant registry lookups.
 *
 * <p>Example usage:
 * <pre>{@code
 * SchemaRegistryClient registry = new SchemaRegistryClient("http://localhost:9094");
 * SchemaDeserializer<MyEvent> deserializer = new SchemaDeserializer<>(registry);
 * DeserializedRecord<MyEvent> record = deserializer.deserialize(bytes, MyEvent.class);
 * System.out.println("Schema ID: " + record.schemaId());
 * System.out.println("Value: " + record.value());
 * }</pre>
 *
 * @param <T> the target value type
 */
public class SchemaDeserializer<T> {

    private static final byte MAGIC_BYTE = 0x00;
    private static final int HEADER_SIZE = 1 + 4; // magic byte + schema ID

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
     * @param data        the wire-format bytes
     * @param targetClass the class to deserialize the JSON payload into
     * @return a {@link DeserializedRecord} containing the schema ID, schema string, and value
     * @throws StreamlineException if the data is malformed or deserialization fails
     */
    public DeserializedRecord<T> deserialize(byte[] data, Class<T> targetClass) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(targetClass, "targetClass must not be null");

        if (data.length < HEADER_SIZE) {
            throw new StreamlineException(
                "Invalid schema wire format: expected at least " + HEADER_SIZE
                    + " bytes, got " + data.length
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte magic = buffer.get();
        if (magic != MAGIC_BYTE) {
            throw new StreamlineException(
                "Invalid magic byte: expected 0x00, got 0x" + String.format("%02x", magic)
            );
        }

        int schemaId = buffer.getInt();
        String schema = resolveSchema(schemaId);

        byte[] payload = new byte[data.length - HEADER_SIZE];
        buffer.get(payload);

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
}
