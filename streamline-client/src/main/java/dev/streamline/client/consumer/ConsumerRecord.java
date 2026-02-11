package dev.streamline.client.consumer;

import dev.streamline.client.Headers;

/**
 * A record received from a consumer poll.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public record ConsumerRecord<K, V>(
    String topic,
    int partition,
    long offset,
    long timestamp,
    K key,
    V value,
    Headers headers
) {

    /**
     * Creates a ConsumerRecord with empty headers.
     */
    public ConsumerRecord(String topic, int partition, long offset, long timestamp, K key, V value) {
        this(topic, partition, offset, timestamp, key, value, Headers.empty());
    }

    /**
     * Returns the topic name.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the partition number.
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

    /**
     * Returns the key.
     */
    public K getKey() {
        return key;
    }

    /**
     * Returns the value.
     */
    public V getValue() {
        return value;
    }

    /**
     * Returns the headers.
     */
    public Headers getHeaders() {
        return headers;
    }
}
