package dev.streamline.client.telemetry;

import dev.streamline.client.Headers;
import dev.streamline.client.RecordMetadata;
import dev.streamline.client.producer.Producer;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A producer wrapper that automatically traces every operation using OpenTelemetry.
 * Delegates all operations to the wrapped producer while adding tracing spans.
 *
 * <p>Usage:
 * <pre>{@code
 * TracedProducer<String, String> traced = new TracedProducer<>(producer);
 * traced.send("topic", "key", "value");  // Automatically traced
 * }</pre>
 */
public class TracedProducer<K, V> implements Closeable {

    private final Producer<K, V> delegate;
    private final StreamlineTracing tracing;

    public TracedProducer(Producer<K, V> delegate) {
        this(delegate, StreamlineTracing.create());
    }

    public TracedProducer(Producer<K, V> delegate, StreamlineTracing tracing) {
        if (delegate == null) throw new NullPointerException("delegate must not be null");
        if (tracing == null) throw new NullPointerException("tracing must not be null");
        this.delegate = delegate;
        this.tracing = tracing;
    }

    public CompletableFuture<RecordMetadata> send(String topic, K key, V value) {
        return send(topic, key, value, null);
    }

    public CompletableFuture<RecordMetadata> send(String topic, K key, V value, Headers headers) {
        Headers h = headers != null ? headers : Headers.empty();
        return tracing.traceProducer(topic, h, () -> delegate.send(topic, key, value, h));
    }

    public CompletableFuture<RecordMetadata> send(String topic, int partition, K key, V value) {
        return send(topic, partition, key, value, null);
    }

    public CompletableFuture<RecordMetadata> send(String topic, int partition, K key, V value, Headers headers) {
        Headers h = headers != null ? headers : Headers.empty();
        return tracing.traceProducer(topic, h, () -> delegate.send(topic, partition, key, value, h));
    }

    public List<CompletableFuture<RecordMetadata>> sendBatch(String topic, List<Map.Entry<K, V>> messages) {
        return tracing.traceProducer(topic, Headers.empty(), () -> delegate.sendBatch(topic, messages));
    }

    public void beginTransaction() { delegate.beginTransaction(); }

    public void commitTransaction() { delegate.commitTransaction(); }

    public void abortTransaction() { delegate.abortTransaction(); }

    public void flush() { delegate.flush(); }

    @Override
    public void close() { delegate.close(); }
}
