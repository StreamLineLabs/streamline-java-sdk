package dev.streamline.client.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SchemaSerializerTest {

    private static final String SUBJECT = "test-topic-value";
    private static final String SCHEMA = "{\"type\":\"object\"}";
    private static final int SCHEMA_ID = 42;
    private static final byte[] EXPECTED_MAGIC = {0x53, 0x4C, 0x00, 0x01};

    private StubSchemaRegistryClient registryClient;

    @BeforeEach
    void setUp() {
        registryClient = new StubSchemaRegistryClient();
        registryClient.setNextSchemaId(SCHEMA_ID);
    }

    // ── Wire format tests ────────────────────────────────────────────────

    @Test
    void serializeProducesCorrectWireFormat() throws Exception {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );

        TestEvent event = new TestEvent("hello", 123);
        byte[] bytes = serializer.serialize("test-topic", event);

        // Header: 4-byte magic + 4-byte schema ID = 8 bytes
        assertTrue(bytes.length > SchemaSerializer.HEADER_SIZE,
            "Wire format must be at least " + SchemaSerializer.HEADER_SIZE + " bytes");

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Validate 4-byte magic marker
        byte[] magic = new byte[4];
        buffer.get(magic);
        assertArrayEquals(EXPECTED_MAGIC, magic, "First 4 bytes must be Streamline magic [0x53,0x4C,0x00,0x01]");

        // Validate schema ID at bytes 4-7
        assertEquals(SCHEMA_ID, buffer.getInt(), "Bytes 4-7 must be big-endian schema ID");

        // Remaining bytes are JSON payload
        byte[] payload = new byte[bytes.length - SchemaSerializer.HEADER_SIZE];
        buffer.get(payload);

        ObjectMapper mapper = new ObjectMapper();
        TestEvent deserialized = mapper.readValue(payload, TestEvent.class);
        assertEquals("hello", deserialized.name);
        assertEquals(123, deserialized.value);
    }

    @Test
    void serializeNullTopicThrows() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );

        assertThrows(NullPointerException.class, () ->
            serializer.serialize(null, new TestEvent("a", 1))
        );
    }

    @Test
    void serializeNullValueThrows() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );

        assertThrows(NullPointerException.class, () ->
            serializer.serialize("topic", null)
        );
    }

    // ── Round-trip tests ─────────────────────────────────────────────────

    @Test
    void roundTripSerializeAndDeserialize() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        TestEvent original = new TestEvent("round-trip", 999);
        byte[] bytes = serializer.serialize("test-topic", original);
        DeserializedRecord<TestEvent> result = deserializer.deserialize(bytes, TestEvent.class);

        assertEquals(SCHEMA_ID, result.schemaId());
        assertEquals(SCHEMA, result.schema());
        assertEquals("round-trip", result.value().name);
        assertEquals(999, result.value().value);
    }

    @Test
    void roundTripWithNestedObject() {
        registryClient.setNextSchemaId(7);
        registryClient.addSchema(7, SCHEMA);

        SchemaSerializer<NestedEvent> serializer = new SchemaSerializer<>(
            registryClient, "nested-subject", SCHEMA, SchemaFormat.JSON
        );
        SchemaDeserializer<NestedEvent> deserializer = new SchemaDeserializer<>(registryClient);

        NestedEvent original = new NestedEvent("outer", new TestEvent("inner", 55));
        byte[] bytes = serializer.serialize("topic", original);
        DeserializedRecord<NestedEvent> result = deserializer.deserialize(bytes, NestedEvent.class);

        assertEquals("outer", result.value().label);
        assertEquals("inner", result.value().event.name);
        assertEquals(55, result.value().event.value);
    }

    // ── Schema ID caching tests ──────────────────────────────────────────

    @Test
    void schemaIdIsCachedAcrossSerializations() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );

        serializer.serialize("topic", new TestEvent("first", 1));
        serializer.serialize("topic", new TestEvent("second", 2));
        serializer.serialize("topic", new TestEvent("third", 3));

        assertEquals(1, registryClient.getRegisterCallCount(),
            "Schema should be registered only once; subsequent calls should use cache");
    }

    @Test
    void differentSubjectsGetSeparateCacheEntries() {
        registryClient.setNextSchemaId(10);
        SchemaSerializer<TestEvent> serializer1 = new SchemaSerializer<>(
            registryClient, "subject-a", SCHEMA, SchemaFormat.JSON
        );
        serializer1.serialize("topic", new TestEvent("a", 1));

        registryClient.setNextSchemaId(20);
        SchemaSerializer<TestEvent> serializer2 = new SchemaSerializer<>(
            registryClient, "subject-b", SCHEMA, SchemaFormat.JSON
        );
        serializer2.serialize("topic", new TestEvent("b", 2));

        assertEquals(2, registryClient.getRegisterCallCount(),
            "Each subject should register independently");
    }

    // ── Auto-register disabled ───────────────────────────────────────────

    @Test
    void autoRegisterDisabledThrowsWhenSchemaNotCached() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, false
        );

        StreamlineException ex = assertThrows(StreamlineException.class, () ->
            serializer.serialize("topic", new TestEvent("fail", 0))
        );
        assertTrue(ex.getMessage().contains("auto-registration is disabled"));
    }

    // ── Deserializer validation tests ────────────────────────────────────

    @Test
    void deserializerRejectsTooShortData() {
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        // 3 bytes detected as Confluent (first byte 0x00) but shorter than 5-byte header
        StreamlineException ex = assertThrows(StreamlineException.class, () ->
            deserializer.deserialize(new byte[]{0x00, 0x00, 0x00}, TestEvent.class)
        );
        assertTrue(ex.getMessage().contains("at least"));
    }

    @Test
    void deserializerRejectsInvalidMagicBytes() {
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        // First byte 0x42 is neither Streamline (0x53) nor Confluent (0x00)
        byte[] bad = new byte[]{0x42, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, '{', '}'};
        StreamlineException ex = assertThrows(StreamlineException.class, () ->
            deserializer.deserialize(bad, TestEvent.class)
        );
        assertTrue(ex.getMessage().contains("wire format"));
    }

    @Test
    void deserializerCachesSchemaById() {
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        byte[] bytes = serializer.serialize("topic", new TestEvent("cached", 1));
        deserializer.deserialize(bytes, TestEvent.class);
        deserializer.deserialize(bytes, TestEvent.class);
        deserializer.deserialize(bytes, TestEvent.class);

        assertEquals(1, registryClient.getGetSchemaCallCount(),
            "Schema should be fetched from registry only once; subsequent calls should use cache");
    }

    // ── Constructor validation tests ─────────────────────────────────────

    @Test
    void serializerRejectsNullArguments() {
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(null, SUBJECT, SCHEMA, SchemaFormat.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, null, SCHEMA, SchemaFormat.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, SUBJECT, null, SchemaFormat.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, SUBJECT, SCHEMA, (SchemaFormat) null));
    }

    @Test
    void deserializerRejectsNullRegistryClient() {
        assertThrows(NullPointerException.class, () ->
            new SchemaDeserializer<>(null));
    }

    // ── Backward-compatible SchemaType constructor tests ──────────────────

    @Test
    void legacySchemaTypeConstructorWorks() {
        registryClient.setNextSchemaId(99);
        registryClient.addSchema(99, SCHEMA);

        @SuppressWarnings("deprecation")
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
        );

        byte[] bytes = serializer.serialize("topic", new TestEvent("legacy", 77));
        assertTrue(bytes.length > SchemaSerializer.HEADER_SIZE);

        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);
        DeserializedRecord<TestEvent> result = deserializer.deserialize(bytes, TestEvent.class);
        assertEquals("legacy", result.value().name);
        assertEquals(77, result.value().value);
    }

    // ── Schema record tests ──────────────────────────────────────────────

    @Test
    void schemaRecordDefensiveCopy() {
        java.util.List<Schema.SchemaReference> refs = new java.util.ArrayList<>();
        refs.add(new Schema.SchemaReference("ref1", "other-subject", 1));
        Schema schema = new Schema(1, "test", 1, SchemaFormat.JSON, "{}", refs);

        assertEquals(1, schema.references().size());
        assertThrows(UnsupportedOperationException.class, () ->
            schema.references().add(new Schema.SchemaReference("ref2", "another", 2))
        );
    }

    @Test
    void schemaRecordNullReferencesDefaultsToEmpty() {
        Schema schema = new Schema(1, "test", 1, SchemaFormat.JSON, "{}", null);
        assertNotNull(schema.references());
        assertTrue(schema.references().isEmpty());
    }

    @Test
    void schemaConvenienceConstructor() {
        Schema schema = new Schema(1, "test", 1, SchemaFormat.AVRO, "{}");
        assertTrue(schema.references().isEmpty());
    }

    // ── WireFormat and Confluent compatibility tests ─────────────────────

    @Test
    void wireFormatDetectStreamline() {
        byte[] data = {0x53, 0x4C, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x7B, 0x7D};
        assertEquals(WireFormat.STREAMLINE, WireFormat.detect(data));
    }

    @Test
    void wireFormatDetectConfluent() {
        byte[] data = {0x00, 0x00, 0x00, 0x00, 0x01, 0x7B, 0x7D};
        assertEquals(WireFormat.CONFLUENT, WireFormat.detect(data));
    }

    @Test
    void wireFormatDetectRejectsUnknown() {
        byte[] data = {0x42, 0x00};
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(data));
    }

    @Test
    void wireFormatDetectRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(null));
    }

    @Test
    void wireFormatHeaderSizes() {
        assertEquals(8, WireFormat.STREAMLINE.headerSize());
        assertEquals(5, WireFormat.CONFLUENT.headerSize());
    }

    @Test
    void confluentSerializerProducesCorrectFormat() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.CONFLUENT
        );

        byte[] bytes = serializer.serialize("topic", new TestEvent("confluent", 99));

        // Confluent header: 1 byte magic (0x00) + 4 bytes schema ID = 5 bytes
        assertTrue(bytes.length > 5);
        assertEquals(0x00, bytes[0], "First byte must be Confluent magic 0x00");

        ByteBuffer buf = ByteBuffer.wrap(bytes, 1, 4);
        assertEquals(SCHEMA_ID, buf.getInt(), "Schema ID must follow magic byte");

        assertEquals(WireFormat.CONFLUENT, serializer.wireFormat());
    }

    @Test
    void confluentRoundTrip() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.CONFLUENT
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        TestEvent original = new TestEvent("confluent-rt", 77);
        byte[] bytes = serializer.serialize("topic", original);
        DeserializedRecord<TestEvent> result = deserializer.deserialize(bytes, TestEvent.class);

        assertEquals(SCHEMA_ID, result.schemaId());
        assertEquals("confluent-rt", result.value().name);
        assertEquals(77, result.value().value);
    }

    @Test
    void deserializerAutoDetectsStreamlineFormat() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.STREAMLINE
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        byte[] bytes = serializer.serialize("topic", new TestEvent("auto", 1));
        assertEquals(0x53, bytes[0], "First byte should be Streamline magic");

        DeserializedRecord<TestEvent> result = deserializer.deserialize(bytes, TestEvent.class);
        assertEquals("auto", result.value().name);
    }

    @Test
    void deserializerAutoDetectsConfluentFormat() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.CONFLUENT
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        byte[] bytes = serializer.serialize("topic", new TestEvent("auto-confluent", 2));
        assertEquals(0x00, bytes[0], "First byte should be Confluent magic");

        DeserializedRecord<TestEvent> result = deserializer.deserialize(bytes, TestEvent.class);
        assertEquals("auto-confluent", result.value().name);
    }

    @Test
    void crossFormatInterop() {
        registryClient.setNextSchemaId(SCHEMA_ID);
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        // Serialize with Streamline, deserialize with auto-detect
        SchemaSerializer<TestEvent> slSerializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.STREAMLINE
        );
        // Serialize with Confluent, deserialize with same auto-detect deserializer
        SchemaSerializer<TestEvent> cfSerializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaFormat.JSON, true, WireFormat.CONFLUENT
        );
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        byte[] slBytes = slSerializer.serialize("topic", new TestEvent("sl", 1));
        byte[] cfBytes = cfSerializer.serialize("topic", new TestEvent("cf", 2));

        // Both should deserialize correctly
        assertEquals("sl", deserializer.deserialize(slBytes, TestEvent.class).value().name);
        assertEquals("cf", deserializer.deserialize(cfBytes, TestEvent.class).value().name);

        // Different header sizes
        assertTrue(slBytes.length > cfBytes.length,
            "Streamline format (8-byte header) should produce larger output than Confluent (5-byte header)");
    }

    // ── Test helpers ─────────────────────────────────────────────────────

    /**
     * Stub implementation of {@link SchemaRegistryClient} for unit testing
     * without network calls.
     */
    static class StubSchemaRegistryClient extends SchemaRegistryClient {

        private int nextSchemaId = 1;
        private int registerCallCount = 0;
        private int getSchemaCallCount = 0;
        private final java.util.Map<Integer, String> schemas = new java.util.concurrent.ConcurrentHashMap<>();

        StubSchemaRegistryClient() {
            super("http://stub:9094");
        }

        void setNextSchemaId(int id) {
            this.nextSchemaId = id;
        }

        void addSchema(int id, String schema) {
            schemas.put(id, schema);
        }

        int getRegisterCallCount() {
            return registerCallCount;
        }

        int getGetSchemaCallCount() {
            return getSchemaCallCount;
        }

        @Override
        public int registerSchema(String subject, String schema, SchemaFormat format) {
            registerCallCount++;
            int id = nextSchemaId;
            schemas.put(id, schema);
            return id;
        }

        @Override
        public String getSchema(int id) {
            getSchemaCallCount++;
            String schema = schemas.get(id);
            if (schema == null) {
                throw new dev.streamline.client.StreamlineException(
                    "Schema not found for ID: " + id
                );
            }
            return schema;
        }
    }

    /**
     * Simple POJO for serialization tests.
     */
    public static class TestEvent {
        public String name;
        public int value;

        public TestEvent() {}

        public TestEvent(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * Nested POJO for testing complex object serialization.
     */
    public static class NestedEvent {
        public String label;
        public TestEvent event;

        public NestedEvent() {}

        public NestedEvent(String label, TestEvent event) {
            this.label = label;
            this.event = event;
        }
    }
}
