package dev.streamline.client.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProducerConfigTest {

    @Test
    void testDefaults() {
        ProducerConfig config = ProducerConfig.defaults();

        assertEquals(16384, config.batchSize());
        assertEquals(1, config.lingerMs());
        assertEquals(1048576, config.maxRequestSize());
        assertEquals("none", config.compressionType());
        assertEquals(3, config.retries());
        assertEquals(100, config.retryBackoffMs());
        assertFalse(config.idempotent());
    }

    @Test
    void testBuilder() {
        ProducerConfig config = ProducerConfig.builder()
            .batchSize(32768)
            .lingerMs(10)
            .maxRequestSize(2097152)
            .compressionType("gzip")
            .retries(5)
            .retryBackoffMs(200)
            .idempotent(true)
            .build();

        assertEquals(32768, config.batchSize());
        assertEquals(10, config.lingerMs());
        assertEquals(2097152, config.maxRequestSize());
        assertEquals("gzip", config.compressionType());
        assertEquals(5, config.retries());
        assertEquals(200, config.retryBackoffMs());
        assertTrue(config.idempotent());
    }

    @Test
    void testBuilderDefaults() {
        ProducerConfig config = ProducerConfig.builder().build();

        assertEquals(16384, config.batchSize());
        assertEquals(1, config.lingerMs());
        assertEquals(1048576, config.maxRequestSize());
        assertEquals("none", config.compressionType());
        assertEquals(3, config.retries());
        assertEquals(100, config.retryBackoffMs());
        assertFalse(config.idempotent());
    }

    @Test
    void testRecordEquality() {
        ProducerConfig c1 = ProducerConfig.defaults();
        ProducerConfig c2 = ProducerConfig.builder().build();
        ProducerConfig c3 = ProducerConfig.builder().batchSize(999).build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
    }

    @Test
    void testNegativeBatchSizeShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            ProducerConfig.builder().batchSize(-1).build());
    }

    @Test
    void testNegativeLingerMsShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            ProducerConfig.builder().lingerMs(-1).build());
    }

    @Test
    void testNegativeMaxRequestSizeShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            ProducerConfig.builder().maxRequestSize(-1).build());
    }

    @Test
    void testNegativeRetriesShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            ProducerConfig.builder().retries(-1).build());
    }

    @Test
    void testNegativeRetryBackoffMsShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            ProducerConfig.builder().retryBackoffMs(-1).build());
    }

    @Test
    void testZeroValuesAreValid() {
        ProducerConfig config = ProducerConfig.builder()
            .batchSize(0)
            .lingerMs(0)
            .maxRequestSize(0)
            .retries(0)
            .retryBackoffMs(0)
            .build();

        assertEquals(0, config.batchSize());
        assertEquals(0, config.lingerMs());
        assertEquals(0, config.maxRequestSize());
        assertEquals(0, config.retries());
        assertEquals(0, config.retryBackoffMs());
    }

    @Test
    void testPartialBuilderOverride() {
        ProducerConfig config = ProducerConfig.builder()
            .batchSize(8192)
            .compressionType("snappy")
            .build();

        assertEquals(8192, config.batchSize());
        assertEquals("snappy", config.compressionType());
        // remaining fields should have defaults
        assertEquals(1, config.lingerMs());
        assertEquals(1048576, config.maxRequestSize());
        assertEquals(3, config.retries());
        assertEquals(100, config.retryBackoffMs());
        assertFalse(config.idempotent());
    }

    @Test
    void testBuilderChainingReturnsSameInstance() {
        ProducerConfig.Builder builder = ProducerConfig.builder();

        assertSame(builder, builder.batchSize(100));
        assertSame(builder, builder.lingerMs(5));
        assertSame(builder, builder.maxRequestSize(2048));
        assertSame(builder, builder.compressionType("lz4"));
        assertSame(builder, builder.retries(1));
        assertSame(builder, builder.retryBackoffMs(50));
        assertSame(builder, builder.idempotent(true));
    }

    @Test
    void testDirectRecordConstruction() {
        ProducerConfig config = new ProducerConfig(1024, 5, 2048, "zstd", 2, 50, true);

        assertEquals(1024, config.batchSize());
        assertEquals(5, config.lingerMs());
        assertEquals(2048, config.maxRequestSize());
        assertEquals("zstd", config.compressionType());
        assertEquals(2, config.retries());
        assertEquals(50, config.retryBackoffMs());
        assertTrue(config.idempotent());
    }

    @Test
    void testDirectConstructionWithNegativeBatchSizeShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProducerConfig(-1, 1, 1048576, "none", 3, 100, false));
    }

    @Test
    void testToStringContainsValues() {
        ProducerConfig config = ProducerConfig.defaults();
        String str = config.toString();

        assertTrue(str.contains("16384"));
        assertTrue(str.contains("none"));
    }
}
