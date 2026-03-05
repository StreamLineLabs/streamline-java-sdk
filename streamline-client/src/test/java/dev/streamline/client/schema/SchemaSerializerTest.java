package dev.streamline.client.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SchemaSerializerTest {

    private static final String SUBJECT = "test-topic-value";
    private static final String SCHEMA = "{\"type\":\"object\"}";
    private static final int SCHEMA_ID = 42;

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
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
        );

        TestEvent event = new TestEvent("hello", 123);
        byte[] bytes = serializer.serialize("test-topic", event);

        // Header: 1 magic byte + 4-byte schema ID
        assertTrue(bytes.length > 5, "Wire format must be at least 5 bytes");

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        assertEquals(0x00, buffer.get(), "First byte must be magic byte 0x00");
        assertEquals(SCHEMA_ID, buffer.getInt(), "Bytes 1-4 must be big-endian schema ID");

        // Remaining bytes are JSON payload
        byte[] payload = new byte[bytes.length - 5];
        buffer.get(payload);

        ObjectMapper mapper = new ObjectMapper();
        TestEvent deserialized = mapper.readValue(payload, TestEvent.class);
        assertEquals("hello", deserialized.name);
        assertEquals(123, deserialized.value);
    }

    @Test
    void serializeNullTopicThrows() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
        );

        assertThrows(NullPointerException.class, () ->
            serializer.serialize(null, new TestEvent("a", 1))
        );
    }

    @Test
    void serializeNullValueThrows() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
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
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
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
            registryClient, "nested-subject", SCHEMA, SchemaType.JSON
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
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
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
            registryClient, "subject-a", SCHEMA, SchemaType.JSON
        );
        serializer1.serialize("topic", new TestEvent("a", 1));

        registryClient.setNextSchemaId(20);
        SchemaSerializer<TestEvent> serializer2 = new SchemaSerializer<>(
            registryClient, "subject-b", SCHEMA, SchemaType.JSON
        );
        serializer2.serialize("topic", new TestEvent("b", 2));

        assertEquals(2, registryClient.getRegisterCallCount(),
            "Each subject should register independently");
    }

    // ── Auto-register disabled ───────────────────────────────────────────

    @Test
    void autoRegisterDisabledThrowsWhenSchemaNotCached() {
        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON, false
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

        StreamlineException ex = assertThrows(StreamlineException.class, () ->
            deserializer.deserialize(new byte[]{0x00, 0x01}, TestEvent.class)
        );
        assertTrue(ex.getMessage().contains("at least"));
    }

    @Test
    void deserializerRejectsInvalidMagicByte() {
        SchemaDeserializer<TestEvent> deserializer = new SchemaDeserializer<>(registryClient);

        byte[] bad = new byte[]{0x01, 0x00, 0x00, 0x00, 0x01, '{', '}'};
        StreamlineException ex = assertThrows(StreamlineException.class, () ->
            deserializer.deserialize(bad, TestEvent.class)
        );
        assertTrue(ex.getMessage().contains("magic byte"));
    }

    @Test
    void deserializerCachesSchemaById() {
        registryClient.addSchema(SCHEMA_ID, SCHEMA);

        SchemaSerializer<TestEvent> serializer = new SchemaSerializer<>(
            registryClient, SUBJECT, SCHEMA, SchemaType.JSON
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
            new SchemaSerializer<>(null, SUBJECT, SCHEMA, SchemaType.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, null, SCHEMA, SchemaType.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, SUBJECT, null, SchemaType.JSON));
        assertThrows(NullPointerException.class, () ->
            new SchemaSerializer<>(registryClient, SUBJECT, SCHEMA, null));
    }

    @Test
    void deserializerRejectsNullRegistryClient() {
        assertThrows(NullPointerException.class, () ->
            new SchemaDeserializer<>(null));
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
        public int register(String subject, String schema, SchemaType type) {
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
