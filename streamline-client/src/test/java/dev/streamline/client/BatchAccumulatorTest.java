package dev.streamline.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchAccumulatorTest {

    @Test
    void testInitialSizeIsZero() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        assertEquals(0, acc.size());
    }

    @Test
    void testAppendIncreasesSize() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        acc.append("a");
        assertEquals(1, acc.size());
        acc.append("b");
        assertEquals(2, acc.size());
    }

    @Test
    void testAppendReturnsFalseWhenNotFull() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(5);
        assertFalse(acc.append("a"));
        assertFalse(acc.append("b"));
    }

    @Test
    void testAppendReturnsTrueWhenFull() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(3);
        assertFalse(acc.append("a"));
        assertFalse(acc.append("b"));
        assertTrue(acc.append("c")); // 3rd element reaches maxBatchSize
    }

    @Test
    void testDrainReturnsAccumulatedRecords() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        acc.append("x");
        acc.append("y");
        acc.append("z");

        List<String> drained = acc.drain();
        assertEquals(3, drained.size());
        assertEquals("x", drained.get(0));
        assertEquals("y", drained.get(1));
        assertEquals("z", drained.get(2));
    }

    @Test
    void testDrainResetsSize() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        acc.append("a");
        acc.append("b");
        assertEquals(2, acc.size());

        acc.drain();
        assertEquals(0, acc.size());
    }

    @Test
    void testDrainOnEmptyReturnsEmptyList() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        List<String> drained = acc.drain();
        assertNotNull(drained);
        assertTrue(drained.isEmpty());
    }

    @Test
    void testAppendAfterDrain() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(10);
        acc.append("first");
        acc.drain();

        acc.append("second");
        assertEquals(1, acc.size());

        List<String> drained = acc.drain();
        assertEquals(1, drained.size());
        assertEquals("second", drained.get(0));
    }

    @Test
    void testBatchSizeOfOne() {
        BatchAccumulator<String> acc = new BatchAccumulator<>(1);
        assertTrue(acc.append("only")); // immediately full
        List<String> drained = acc.drain();
        assertEquals(1, drained.size());
    }

    @Test
    void testMultipleDrainCycles() {
        BatchAccumulator<Integer> acc = new BatchAccumulator<>(2);
        acc.append(1);
        acc.append(2);
        List<Integer> batch1 = acc.drain();

        acc.append(3);
        acc.append(4);
        List<Integer> batch2 = acc.drain();

        assertEquals(List.of(1, 2), batch1);
        assertEquals(List.of(3, 4), batch2);
    }
}
