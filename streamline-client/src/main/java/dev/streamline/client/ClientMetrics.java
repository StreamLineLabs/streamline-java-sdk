package dev.streamline.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe client-side metrics collection.
 *
 * <p>Tracks produce/consume throughput, latency, and errors using lock-free
 * atomic counters. Suitable for high-throughput scenarios where contention
 * must be minimised.
 *
 * <p>Example usage:
 * <pre>{@code
 * ClientMetrics metrics = new ClientMetrics();
 *
 * long start = System.currentTimeMillis();
 * producer.send(topic, key, value).get();
 * metrics.recordProduce(1, value.length(), System.currentTimeMillis() - start);
 *
 * Snapshot snap = metrics.snapshot();
 * System.out.println("Avg produce latency: " + snap.produceLatencyAvgMs() + " ms");
 * }</pre>
 */
public class ClientMetrics {

    private final AtomicLong messagesProduced = new AtomicLong();
    private final AtomicLong messagesConsumed = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong errorsTotal = new AtomicLong();
    private final AtomicLong produceLatencyTotalMs = new AtomicLong();
    private final AtomicLong consumeLatencyTotalMs = new AtomicLong();
    private final long startTimeMs = System.currentTimeMillis();

    /**
     * Records a produce operation.
     *
     * @param count     number of messages produced
     * @param bytes     total bytes sent
     * @param latencyMs time taken in milliseconds
     */
    public void recordProduce(int count, long bytes, long latencyMs) {
        messagesProduced.addAndGet(count);
        bytesSent.addAndGet(bytes);
        produceLatencyTotalMs.addAndGet(latencyMs);
    }

    /**
     * Records a consume operation.
     *
     * @param count     number of messages consumed
     * @param bytes     total bytes received
     * @param latencyMs time taken in milliseconds
     */
    public void recordConsume(int count, long bytes, long latencyMs) {
        messagesConsumed.addAndGet(count);
        bytesReceived.addAndGet(bytes);
        consumeLatencyTotalMs.addAndGet(latencyMs);
    }

    /** Records a single error. */
    public void recordError() {
        errorsTotal.incrementAndGet();
    }

    /**
     * Takes a point-in-time snapshot of all metrics.
     *
     * @return an immutable snapshot of current counter values
     */
    public Snapshot snapshot() {
        long produced = messagesProduced.get();
        long consumed = messagesConsumed.get();
        double produceAvg = produced > 0
                ? (double) produceLatencyTotalMs.get() / produced
                : 0.0;
        double consumeAvg = consumed > 0
                ? (double) consumeLatencyTotalMs.get() / consumed
                : 0.0;

        return new Snapshot(
                produced,
                consumed,
                bytesSent.get(),
                bytesReceived.get(),
                errorsTotal.get(),
                produceAvg,
                consumeAvg,
                System.currentTimeMillis() - startTimeMs
        );
    }

    /**
     * Immutable point-in-time snapshot of client metrics.
     *
     * @param messagesProduced    total messages produced
     * @param messagesConsumed    total messages consumed
     * @param bytesSent           total bytes sent
     * @param bytesReceived       total bytes received
     * @param errorsTotal         total errors recorded
     * @param produceLatencyAvgMs average produce latency in milliseconds
     * @param consumeLatencyAvgMs average consume latency in milliseconds
     * @param uptimeMs            client uptime in milliseconds
     */
    public record Snapshot(
            long messagesProduced,
            long messagesConsumed,
            long bytesSent,
            long bytesReceived,
            long errorsTotal,
            double produceLatencyAvgMs,
            double consumeLatencyAvgMs,
            long uptimeMs
    ) {}
}
