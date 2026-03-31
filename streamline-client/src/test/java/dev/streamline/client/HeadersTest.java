package dev.streamline.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeadersTest {

    @Test
    void testOf() {
        Map<String, String> map = Map.of("key1", "val1", "key2", "val2");
        Headers headers = Headers.of(map);

        assertEquals("val1", headers.get("key1"));
        assertEquals("val2", headers.get("key2"));
        assertFalse(headers.isEmpty());
    }

    @Test
    void testBuilder() {
        Headers headers = Headers.builder()
            .add("content-type", "application/json")
            .add("trace-id", "abc-123")
            .build();

        assertEquals("application/json", headers.get("content-type"));
        assertEquals("abc-123", headers.get("trace-id"));
        assertEquals(2, headers.toMap().size());
    }

    @Test
    void testEmpty() {
        Headers headers = Headers.empty();

        assertTrue(headers.isEmpty());
        assertNull(headers.get("any-key"));
        assertEquals(0, headers.toMap().size());
    }

    @Test
    void testGet() {
        Headers headers = Headers.of(Map.of("existing", "value"));

        assertEquals("value", headers.get("existing"));
        assertNull(headers.get("nonexistent"));
    }

    @Test
    void testToMap() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("a", "1");
        original.put("b", "2");

        Headers headers = Headers.of(original);
        Map<String, String> result = headers.toMap();

        assertEquals(2, result.size());
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }

    @Test
    void testIsEmpty() {
        assertTrue(Headers.empty().isEmpty());
        assertTrue(Headers.of(Map.of()).isEmpty());
        assertFalse(Headers.of(Map.of("k", "v")).isEmpty());
    }

    @Test
    void testBuilderChaining() {
        Headers.Builder builder = Headers.builder();
        Headers.Builder returned = builder.add("key", "value");

        assertSame(builder, returned);
    }

    @Test
    void testImmutability() {
        Headers headers = Headers.builder()
            .add("key", "value")
            .build();

        Map<String, String> map = headers.toMap();
        assertThrows(UnsupportedOperationException.class, () -> map.put("new", "entry"));
    }
}
