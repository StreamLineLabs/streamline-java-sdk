package dev.streamline.client.telemetry;

import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.io.Closeable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A consumer wrapper that automatically traces every poll operation using OpenTelemetry.
 * Delegates all operations to the wrapped consumer while adding tracing spans.
 *
 * <p>Usage:
 * <pre>{@code
 * TracedConsumer<String, String> traced = new TracedConsumer<>(consumer, "my-topic");
 * traced.subscribe();
 * List<ConsumerRecord<String, String>> records = traced.poll(Duration.ofMillis(100));
 * }</pre>
 */
public class TracedConsumer<K, V> implements Closeable {

    private final Consumer<K, V> delegate;
    private final StreamlineTracing tracing;
    private final String topic;

    public TracedConsumer(Consumer<K, V> delegate, String topic) {
        this(delegate, topic, StreamlineTracing.create());
    }

    public TracedConsumer(Consumer<K, V> delegate, String topic, StreamlineTracing tracing) {
        if (delegate == null) throw new NullPointerException("delegate must not be null");
        if (topic == null) throw new NullPointerException("topic must not be null");
        if (tracing == null) throw new NullPointerException("tracing must not be null");
        this.delegate = delegate;
        this.topic = topic;
        this.tracing = tracing;
    }

    public String topic() {
        return topic;
    }

    public void subscribe() {
        delegate.subscribe();
    }

    public void assign(Collection<TopicPartition> partitions) {
        delegate.assign(partitions);
    }

    public List<ConsumerRecord<K, V>> poll(Duration timeout) {
        return tracing.traceConsumer(topic, () -> delegate.poll(timeout));
    }

    public void commitSync() { delegate.commitSync(); }

    public void commitAsync() { delegate.commitAsync(); }

    public void seekToBeginning() { delegate.seekToBeginning(); }

    public void seekToEnd() { delegate.seekToEnd(); }

    public void seek(int partition, long offset) { delegate.seek(partition, offset); }

    public void seekToTimestamp(long timestamp) { delegate.seekToTimestamp(timestamp); }

    public long position(int partition) { return delegate.position(partition); }

    public Set<TopicPartition> assignment() { return delegate.assignment(); }

    public Set<String> subscription() { return delegate.subscription(); }

    public Map<String, List<PartitionInfo>> listTopics() { return delegate.listTopics(); }

    public void pause() { delegate.pause(); }

    public void resume() { delegate.resume(); }

    @Override
    public void close() { delegate.close(); }
}
