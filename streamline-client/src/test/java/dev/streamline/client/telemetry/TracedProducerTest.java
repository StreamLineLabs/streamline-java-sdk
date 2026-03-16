package dev.streamline.client.telemetry;

import dev.streamline.client.Headers;
import dev.streamline.client.RecordMetadata;
import dev.streamline.client.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TracedProducerTest {

    private Producer<String, String> mockProducer;
    private StreamlineTracing mockTracing;
    private TracedProducer<String, String> tracedProducer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockProducer = mock(Producer.class);
        mockTracing = mock(StreamlineTracing.class);
        tracedProducer = new TracedProducer<>(mockProducer, mockTracing);

        // Default: tracing executes the supplier transparently
        when(mockTracing.traceProducer(anyString(), any(Headers.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return supplier.get();
                });
    }

    @Test
    void sendDelegatesToProducer() {
        CompletableFuture<RecordMetadata> expected = CompletableFuture.completedFuture(
                new RecordMetadata("topic", 0, 1L, System.currentTimeMillis()));
        when(mockProducer.send(eq("topic"), eq("key"), eq("value"), any(Headers.class)))
                .thenReturn(expected);

        CompletableFuture<RecordMetadata> result = tracedProducer.send("topic", "key", "value");

        assertSame(expected, result);
        verify(mockProducer).send(eq("topic"), eq("key"), eq("value"), any(Headers.class));
    }

    @Test
    void sendWithHeadersDelegatesToProducer() {
        Headers headers = Headers.builder().add("h1", "v1").build();
        CompletableFuture<RecordMetadata> expected = CompletableFuture.completedFuture(
                new RecordMetadata("topic", 0, 1L, System.currentTimeMillis()));
        when(mockProducer.send("topic", "key", "value", headers)).thenReturn(expected);

        CompletableFuture<RecordMetadata> result = tracedProducer.send("topic", "key", "value", headers);

        assertSame(expected, result);
        verify(mockProducer).send("topic", "key", "value", headers);
    }

    @Test
    void sendWithPartitionDelegatesToProducer() {
        CompletableFuture<RecordMetadata> expected = CompletableFuture.completedFuture(
                new RecordMetadata("topic", 2, 5L, System.currentTimeMillis()));
        when(mockProducer.send(eq("topic"), eq(2), eq("key"), eq("value"), any(Headers.class)))
                .thenReturn(expected);

        CompletableFuture<RecordMetadata> result = tracedProducer.send("topic", 2, "key", "value");

        assertSame(expected, result);
        verify(mockProducer).send(eq("topic"), eq(2), eq("key"), eq("value"), any(Headers.class));
    }

    @Test
    void sendWithPartitionAndHeadersDelegatesToProducer() {
        Headers headers = Headers.builder().add("h1", "v1").build();
        CompletableFuture<RecordMetadata> expected = CompletableFuture.completedFuture(
                new RecordMetadata("topic", 2, 5L, System.currentTimeMillis()));
        when(mockProducer.send("topic", 2, "key", "value", headers)).thenReturn(expected);

        CompletableFuture<RecordMetadata> result = tracedProducer.send("topic", 2, "key", "value", headers);

        assertSame(expected, result);
        verify(mockProducer).send("topic", 2, "key", "value", headers);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendBatchDelegatesToProducer() {
        List<Map.Entry<String, String>> messages = List.of(Map.entry("k1", "v1"), Map.entry("k2", "v2"));
        List<CompletableFuture<RecordMetadata>> expected = List.of(
                CompletableFuture.completedFuture(new RecordMetadata("topic", 0, 0L, System.currentTimeMillis())),
                CompletableFuture.completedFuture(new RecordMetadata("topic", 0, 1L, System.currentTimeMillis()))
        );
        when(mockProducer.sendBatch("topic", messages)).thenReturn(expected);

        List<CompletableFuture<RecordMetadata>> result = tracedProducer.sendBatch("topic", messages);

        assertSame(expected, result);
        verify(mockProducer).sendBatch("topic", messages);
    }

    @Test
    void sendInvokesTracing() {
        when(mockProducer.send(anyString(), any(), any(), any(Headers.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        tracedProducer.send("my-topic", "key", "value");

        verify(mockTracing).traceProducer(eq("my-topic"), any(Headers.class), any(Supplier.class));
    }

    @Test
    void sendWithNullHeadersUsesEmptyHeaders() {
        when(mockProducer.send(anyString(), any(), any(), any(Headers.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        tracedProducer.send("topic", "key", "value", null);

        verify(mockProducer).send(eq("topic"), eq("key"), eq("value"), argThat(Headers::isEmpty));
    }

    @Test
    void beginTransactionDelegates() {
        tracedProducer.beginTransaction();
        verify(mockProducer).beginTransaction();
    }

    @Test
    void commitTransactionDelegates() {
        tracedProducer.commitTransaction();
        verify(mockProducer).commitTransaction();
    }

    @Test
    void abortTransactionDelegates() {
        tracedProducer.abortTransaction();
        verify(mockProducer).abortTransaction();
    }

    @Test
    void flushDelegates() {
        tracedProducer.flush();
        verify(mockProducer).flush();
    }

    @Test
    void closeDelegates() {
        tracedProducer.close();
        verify(mockProducer).close();
    }

    @Test
    void constructorAcceptsCustomTracing() {
        StreamlineTracing customTracing = mock(StreamlineTracing.class);
        TracedProducer<String, String> producer = new TracedProducer<>(mockProducer, customTracing);
        assertNotNull(producer);
    }

    @Test
    void constructorWithNullDelegateThrows() {
        assertThrows(NullPointerException.class,
                () -> new TracedProducer<>(null, mockTracing));
    }

    @Test
    void constructorWithNullTracingThrows() {
        assertThrows(NullPointerException.class,
                () -> new TracedProducer<>(mockProducer, null));
    }
}
