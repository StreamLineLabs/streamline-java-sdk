package dev.streamline.client.telemetry;

import dev.streamline.client.Headers;
import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TracedConsumerTest {

    private Consumer<String, String> mockConsumer;
    private StreamlineTracing mockTracing;
    private TracedConsumer<String, String> tracedConsumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockConsumer = mock(Consumer.class);
        mockTracing = mock(StreamlineTracing.class);
        tracedConsumer = new TracedConsumer<>(mockConsumer, "test-topic", mockTracing);

        // Default: tracing executes the supplier transparently
        when(mockTracing.traceConsumer(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Test
    void topicReturnsConfiguredTopic() {
        assertEquals("test-topic", tracedConsumer.topic());
    }

    @Test
    void subscribeDelegates() {
        tracedConsumer.subscribe();
        verify(mockConsumer).subscribe();
    }

    @Test
    void assignDelegates() {
        Collection<TopicPartition> partitions = List.of(new TopicPartition("test-topic", 0));
        tracedConsumer.assign(partitions);
        verify(mockConsumer).assign(partitions);
    }

    @Test
    void pollDelegatesToConsumer() {
        Duration timeout = Duration.ofMillis(500);
        List<ConsumerRecord<String, String>> expected = List.of(
                new ConsumerRecord<>("test-topic", 0, 0L, System.currentTimeMillis(), "key", "value", Headers.empty())
        );
        when(mockConsumer.poll(timeout)).thenReturn(expected);

        List<ConsumerRecord<String, String>> result = tracedConsumer.poll(timeout);

        assertSame(expected, result);
        verify(mockConsumer).poll(timeout);
    }

    @Test
    void pollInvokesTracing() {
        when(mockConsumer.poll(any(Duration.class))).thenReturn(List.of());

        tracedConsumer.poll(Duration.ofMillis(100));

        verify(mockTracing).traceConsumer(eq("test-topic"), any(Supplier.class));
    }

    @Test
    void commitSyncDelegates() {
        tracedConsumer.commitSync();
        verify(mockConsumer).commitSync();
    }

    @Test
    void commitAsyncDelegates() {
        tracedConsumer.commitAsync();
        verify(mockConsumer).commitAsync();
    }

    @Test
    void seekToBeginningDelegates() {
        tracedConsumer.seekToBeginning();
        verify(mockConsumer).seekToBeginning();
    }

    @Test
    void seekToEndDelegates() {
        tracedConsumer.seekToEnd();
        verify(mockConsumer).seekToEnd();
    }

    @Test
    void seekDelegates() {
        tracedConsumer.seek(2, 100L);
        verify(mockConsumer).seek(2, 100L);
    }

    @Test
    void seekToTimestampDelegates() {
        tracedConsumer.seekToTimestamp(1234567890L);
        verify(mockConsumer).seekToTimestamp(1234567890L);
    }

    @Test
    void positionDelegates() {
        when(mockConsumer.position(1)).thenReturn(42L);

        long pos = tracedConsumer.position(1);

        assertEquals(42L, pos);
        verify(mockConsumer).position(1);
    }

    @Test
    void assignmentDelegates() {
        Set<TopicPartition> expected = Set.of(new TopicPartition("test-topic", 0));
        when(mockConsumer.assignment()).thenReturn(expected);

        Set<TopicPartition> result = tracedConsumer.assignment();

        assertSame(expected, result);
    }

    @Test
    void subscriptionDelegates() {
        Set<String> expected = Set.of("test-topic");
        when(mockConsumer.subscription()).thenReturn(expected);

        Set<String> result = tracedConsumer.subscription();

        assertSame(expected, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void listTopicsDelegates() {
        Map<String, List<PartitionInfo>> expected = Map.of();
        when(mockConsumer.listTopics()).thenReturn(expected);

        Map<String, List<PartitionInfo>> result = tracedConsumer.listTopics();

        assertSame(expected, result);
    }

    @Test
    void pauseDelegates() {
        tracedConsumer.pause();
        verify(mockConsumer).pause();
    }

    @Test
    void resumeDelegates() {
        tracedConsumer.resume();
        verify(mockConsumer).resume();
    }

    @Test
    void closeDelegates() {
        tracedConsumer.close();
        verify(mockConsumer).close();
    }

    @Test
    void constructorAcceptsCustomTracing() {
        StreamlineTracing customTracing = mock(StreamlineTracing.class);
        TracedConsumer<String, String> consumer = new TracedConsumer<>(mockConsumer, "topic", customTracing);
        assertNotNull(consumer);
    }

    @Test
    void constructorWithNullDelegateThrows() {
        assertThrows(NullPointerException.class,
                () -> new TracedConsumer<>(null, "topic", mockTracing));
    }

    @Test
    void constructorWithNullTopicThrows() {
        assertThrows(NullPointerException.class,
                () -> new TracedConsumer<>(mockConsumer, null, mockTracing));
    }

    @Test
    void constructorWithNullTracingThrows() {
        assertThrows(NullPointerException.class,
                () -> new TracedConsumer<>(mockConsumer, "topic", null));
    }
}
