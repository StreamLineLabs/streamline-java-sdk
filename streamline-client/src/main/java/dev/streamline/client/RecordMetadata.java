package dev.streamline.client;

/**
 * Metadata for a produced record.
 */
public record RecordMetadata(
    String topic,
    int partition,
    long offset,
    long timestamp
) {

    /**
     * Returns the topic name.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the partition.
     */
    public int getPartition() {
        return partition;
    }

    /**
     * Returns the offset.
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Returns the timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
