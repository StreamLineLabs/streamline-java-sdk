package dev.streamline.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ClientMetricsTest {

    @Test
    void recordProduceIncrementsCounters() {
        var metrics = new ClientMetrics();

        metrics.recordProduce(5, 1024, 50);

        var snap = metrics.snapshot();
        assertEquals(5, snap.messagesProduced());
        assertEquals(1024, snap.bytesSent());
    }

    @Test
    void recordConsumeIncrementsCounters() {
        var metrics = new ClientMetrics();

        metrics.recordConsume(10, 2048, 30);

        var snap = metrics.snapshot();
        assertEquals(10, snap.messagesConsumed());
        assertEquals(2048, snap.bytesReceived());
    }

    @Test
    void recordErrorIncrementsCounter() {
        var metrics = new ClientMetrics();

        metrics.recordError();
        metrics.recordError();
        metrics.recordError();

        assertEquals(3, metrics.snapshot().errorsTotal());
    }

    @Test
    void snapshotReturnsCorrectAverages() {
        var metrics = new ClientMetrics();

        metrics.recordProduce(2, 100, 40);
        metrics.recordProduce(3, 200, 60);
        metrics.recordConsume(4, 300, 80);

        var snap = metrics.snapshot();
        // Total produce latency: 100ms, total produced: 5 → avg = 20.0
        assertEquals(20.0, snap.produceLatencyAvgMs(), 0.001);
        // Total consume latency: 80ms, total consumed: 4 → avg = 20.0
        assertEquals(20.0, snap.consumeLatencyAvgMs(), 0.001);
    }

    @Test
    void snapshotWithZeroValuesDoesNotDivideByZero() {
        var metrics = new ClientMetrics();

        var snap = metrics.snapshot();

        assertEquals(0, snap.messagesProduced());
        assertEquals(0, snap.messagesConsumed());
        assertEquals(0.0, snap.produceLatencyAvgMs(), 0.001);
        assertEquals(0.0, snap.consumeLatencyAvgMs(), 0.001);
    }

    @Test
    void uptimeIsPositive() throws Exception {
        var metrics = new ClientMetrics();

        Thread.sleep(10);

        assertTrue(metrics.snapshot().uptimeMs() >= 10);
    }

    @Test
    void threadSafetyConcurrentProduce() throws Exception {
        var metrics = new ClientMetrics();
        int threads = 8;
        int iterationsPerThread = 1000;
        var latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < iterationsPerThread; i++) {
                    metrics.recordProduce(1, 10, 1);
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        var snap = metrics.snapshot();
        assertEquals(threads * iterationsPerThread, snap.messagesProduced());
        assertEquals(threads * iterationsPerThread * 10L, snap.bytesSent());
    }

    @Test
    void multipleOperationsAccumulate() {
        var metrics = new ClientMetrics();

        metrics.recordProduce(1, 50, 10);
        metrics.recordConsume(2, 100, 20);
        metrics.recordError();
        metrics.recordProduce(3, 150, 30);
        metrics.recordConsume(4, 200, 40);

        var snap = metrics.snapshot();
        assertEquals(4, snap.messagesProduced());
        assertEquals(6, snap.messagesConsumed());
        assertEquals(200, snap.bytesSent());
        assertEquals(300, snap.bytesReceived());
        assertEquals(1, snap.errorsTotal());
    }
}
