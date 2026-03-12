package dev.streamline.client.schema;

import java.util.List;
import java.util.Objects;

/**
 * Represents a schema registered in the Streamline schema registry.
 *
 * <p>Each schema is uniquely identified by its {@link #id()} (a global integer
 * assigned by the registry) and is associated with a {@link #subject()} and
 * {@link #version()} pair. The raw schema definition is available via
 * {@link #schema()} and its encoding format via {@link #format()}.
 *
 * <p>Schemas may reference other schemas (e.g. Protobuf imports or Avro named types)
 * through the {@link #references()} list.
 *
 * @param id         the globally unique schema ID assigned by the registry
 * @param subject    the subject this schema belongs to (e.g. {@code "orders-value"})
 * @param version    the version number within the subject (1-based)
 * @param format     the serialization format
 * @param schema     the raw schema definition string
 * @param references other schemas referenced by this schema
 */
public record Schema(
    int id,
    String subject,
    int version,
    SchemaFormat format,
    String schema,
    List<SchemaReference> references
) {

    /**
     * Compact constructor that enforces non-null invariants and creates
     * a defensive copy of the references list.
     */
    public Schema {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        references = references == null ? List.of() : List.copyOf(references);
    }

    /**
     * Convenience constructor for schemas with no references.
     */
    public Schema(int id, String subject, int version, SchemaFormat format, String schema) {
        this(id, subject, version, format, schema, List.of());
    }

    /**
     * A reference from one schema to another (e.g. a Protobuf import).
     *
     * @param name    the reference name as it appears in the schema
     * @param subject the subject of the referenced schema
     * @param version the version of the referenced schema
     */
    public record SchemaReference(String name, String subject, int version) {

        public SchemaReference {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(subject, "subject must not be null");
        }
    }
}
