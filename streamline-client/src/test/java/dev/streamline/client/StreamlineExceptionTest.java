package dev.streamline.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamlineExceptionTest {

    @Test
    void testSimpleConstructor() {
        StreamlineException ex = new StreamlineException("something failed");

        assertEquals("something failed", ex.getMessage());
        assertNull(ex.getCause());
        assertNull(ex.getErrorCode());
        assertNull(ex.getHint());
    }

    @Test
    void testConstructorWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        StreamlineException ex = new StreamlineException("wrapped", cause);

        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getErrorCode());
        assertNull(ex.getHint());
    }

    @Test
    void testConstructorWithErrorCode() {
        StreamlineException ex = new StreamlineException("bad request", "BAD_REQUEST");

        assertEquals("bad request", ex.getMessage());
        assertNull(ex.getCause());
        assertEquals("BAD_REQUEST", ex.getErrorCode());
        assertNull(ex.getHint());
    }

    @Test
    void testFullConstructor() {
        RuntimeException cause = new RuntimeException("io error");
        StreamlineException ex = new StreamlineException("failed", cause, "IO_ERROR", "Check network");

        assertEquals("failed", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertEquals("IO_ERROR", ex.getErrorCode());
        assertEquals("Check network", ex.getHint());
    }

    @Test
    void testTopicNotFound() {
        StreamlineException ex = StreamlineException.topicNotFound("my-topic");

        assertEquals("Topic not found: my-topic", ex.getMessage());
        assertEquals("TOPIC_NOT_FOUND", ex.getErrorCode());
        assertNotNull(ex.getHint());
        assertTrue(ex.getHint().contains("my-topic"));
        assertNull(ex.getCause());
    }

    @Test
    void testConnectionFailed() {
        RuntimeException cause = new RuntimeException("connection refused");
        StreamlineException ex = StreamlineException.connectionFailed("localhost:9092", cause);

        assertEquals("Failed to connect to localhost:9092", ex.getMessage());
        assertEquals("CONNECTION_FAILED", ex.getErrorCode());
        assertSame(cause, ex.getCause());
        assertNotNull(ex.getHint());
    }

    @Test
    void testTimeout() {
        StreamlineException ex = StreamlineException.timeout("produce");

        assertEquals("Operation timed out: produce", ex.getMessage());
        assertEquals("TIMEOUT", ex.getErrorCode());
        assertNotNull(ex.getHint());
        assertNull(ex.getCause());
    }

    @Test
    void testErrorCodeAccessor() {
        StreamlineException ex = new StreamlineException("msg", null, "CUSTOM_CODE", null);
        assertEquals("CUSTOM_CODE", ex.getErrorCode());
    }

    @Test
    void testHintAccessor() {
        StreamlineException ex = new StreamlineException("msg", null, null, "Try again later");
        assertEquals("Try again later", ex.getHint());
    }

    @Test
    void testIsRuntimeException() {
        StreamlineException ex = new StreamlineException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
