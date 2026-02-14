package dev.streamline.client.consumer;

import dev.streamline.client.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerRecordTest {

    @Test
    void testFullConstructor() {
        Headers headers = Headers.builder().add("h1", "v1").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "topic", 2, 100L, 1700000000L, "key", "value", headers
        );

        assertEquals("topic", record.topic());
        assertEquals(2, record.partition());
        assertEquals(100L, record.offset());
        assertEquals(1700000000L, record.timestamp());
        assertEquals("key", record.key());
        assertEquals("value", record.value());
        assertSame(headers, record.headers());
    }

    @Test
    void testConstructorWithoutHeaders() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "topic", 0, 50L, 1700000000L, "key", "value"
        );

        assertEquals("topic", record.topic());
        assertEquals("key", record.key());
        assertEquals("value", record.value());
        assertNotNull(record.headers());
        assertTrue(record.headers().isEmpty());
    }

    @Test
    void testGetterMethods() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "events", 1, 200L, 1700000001L, "k", "v"
        );

        assertEquals("events", record.getTopic());
        assertEquals(1, record.getPartition());
        assertEquals(200L, record.getOffset());
        assertEquals(1700000001L, record.getTimestamp());
        assertEquals("k", record.getKey());
        assertEquals("v", record.getValue());
        assertNotNull(record.getHeaders());
    }

    @Test
    void testRecordEquality() {
        Headers headers = Headers.empty();
        ConsumerRecord<String, String> r1 = new ConsumerRecord<>(
            "topic", 0, 10L, 1000L, "key", "value", headers
        );
        ConsumerRecord<String, String> r2 = new ConsumerRecord<>(
            "topic", 0, 10L, 1000L, "key", "value", headers
        );
        ConsumerRecord<String, String> r3 = new ConsumerRecord<>(
            "topic", 0, 11L, 1000L, "key", "value", headers
        );

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
    }

    @Test
    void testDifferentTypes() {
        ConsumerRecord<Integer, byte[]> record = new ConsumerRecord<>(
            "binary-topic", 0, 1L, 1000L, 42, new byte[]{1, 2, 3}
        );

        assertEquals(42, record.key());
        assertArrayEquals(new byte[]{1, 2, 3}, record.value());
        assertEquals("binary-topic", record.topic());
    }
}
