package dev.streamline.client.schema;

/**
 * Supported schema serialization formats for the Streamline schema registry.
 *
 * <p>Each format defines how message payloads are encoded and validated
 * against registered schemas.
 */
public enum SchemaFormat {

    /** Apache Avro binary or JSON encoding. */
    AVRO,

    /** JSON Schema (draft-07 or later). */
    JSON,

    /** Protocol Buffers (proto3). */
    PROTOBUF
}
