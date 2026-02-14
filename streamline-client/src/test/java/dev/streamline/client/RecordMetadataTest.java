package dev.streamline.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordMetadataTest {

    @Test
    void testRecordComponents() {
        RecordMetadata metadata = new RecordMetadata("my-topic", 3, 42L, 1700000000L);

        assertEquals("my-topic", metadata.topic());
        assertEquals(3, metadata.partition());
        assertEquals(42L, metadata.offset());
        assertEquals(1700000000L, metadata.timestamp());
    }

    @Test
    void testGetterMethods() {
        RecordMetadata metadata = new RecordMetadata("events", 1, 100L, 1700000001L);

        assertEquals("events", metadata.getTopic());
        assertEquals(1, metadata.getPartition());
        assertEquals(100L, metadata.getOffset());
        assertEquals(1700000001L, metadata.getTimestamp());
    }

    @Test
    void testEquality() {
        RecordMetadata m1 = new RecordMetadata("topic", 0, 10L, 1000L);
        RecordMetadata m2 = new RecordMetadata("topic", 0, 10L, 1000L);
        RecordMetadata m3 = new RecordMetadata("topic", 0, 11L, 1000L);

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3);
    }
}
