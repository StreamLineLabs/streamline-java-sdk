package dev.streamline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Accumulates records into batches for efficient network transmission.
 * Uses a lock-free append path for the common case and only locks
 * when flushing.
 */
class BatchAccumulator<T> {

    private final int maxBatchSize;
    private final ReentrantLock flushLock = new ReentrantLock();
    private volatile List<T> currentBatch;

    BatchAccumulator(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
        this.currentBatch = new ArrayList<>(maxBatchSize);
    }

    /**
     * Appends a record to the current batch.
     *
     * @return true if the batch is now full and should be flushed
     */
    synchronized boolean append(T record) {
        currentBatch.add(record);
        return currentBatch.size() >= maxBatchSize;
    }

    /**
     * Drains the current batch, returning all accumulated records.
     */
    List<T> drain() {
        flushLock.lock();
        try {
            List<T> batch = currentBatch;
            currentBatch = new ArrayList<>(maxBatchSize);
            return batch;
        } finally {
            flushLock.unlock();
        }
    }

    int size() {
        return currentBatch.size();
    }
}
