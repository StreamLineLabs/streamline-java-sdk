package dev.streamline.client.schema;

/**
 * Holds the result of deserializing a schema-encoded message.
 *
 * @param schemaId the schema ID extracted from the wire format header
 * @param schema   the schema string retrieved from the registry
 * @param value    the deserialized value
 * @param <T>      the value type
 */
public record DeserializedRecord<T>(
    int schemaId,
    String schema,
    T value
) {}
