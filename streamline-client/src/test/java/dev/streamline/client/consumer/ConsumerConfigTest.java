package dev.streamline.client.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerConfigTest {

    @Test
    void testDefaults() {
        ConsumerConfig config = ConsumerConfig.defaults();

        assertNull(config.groupId());
        assertEquals("earliest", config.autoOffsetReset());
        assertTrue(config.enableAutoCommit());
        assertEquals(5000, config.autoCommitIntervalMs());
        assertEquals(30000, config.sessionTimeoutMs());
        assertEquals(3000, config.heartbeatIntervalMs());
        assertEquals(500, config.maxPollRecords());
        assertEquals(300000, config.maxPollIntervalMs());
    }

    @Test
    void testBuilder() {
        ConsumerConfig config = ConsumerConfig.builder()
            .groupId("my-group")
            .autoOffsetReset("latest")
            .enableAutoCommit(false)
            .autoCommitIntervalMs(10000)
            .sessionTimeoutMs(60000)
            .heartbeatIntervalMs(5000)
            .maxPollRecords(1000)
            .maxPollIntervalMs(600000)
            .build();

        assertEquals("my-group", config.groupId());
        assertEquals("latest", config.autoOffsetReset());
        assertFalse(config.enableAutoCommit());
        assertEquals(10000, config.autoCommitIntervalMs());
        assertEquals(60000, config.sessionTimeoutMs());
        assertEquals(5000, config.heartbeatIntervalMs());
        assertEquals(1000, config.maxPollRecords());
        assertEquals(600000, config.maxPollIntervalMs());
    }

    @Test
    void testBuilderGroupId() {
        ConsumerConfig config = ConsumerConfig.builder()
            .groupId("special-group")
            .build();

        assertEquals("special-group", config.groupId());
        assertEquals("earliest", config.autoOffsetReset());
        assertTrue(config.enableAutoCommit());
    }

    @Test
    void testRecordEquality() {
        ConsumerConfig c1 = ConsumerConfig.defaults();
        ConsumerConfig c2 = ConsumerConfig.builder().build();
        ConsumerConfig c3 = ConsumerConfig.builder().groupId("different").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
    }
}
