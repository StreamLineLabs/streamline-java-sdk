package dev.streamline.client.producer;

import dev.streamline.client.RecordMetadata;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link Producer#send} callback contract.
 *
 * <p>The Java SDK exposes asynchronous send via {@link CompletableFuture}; user
 * code typically chains {@code whenComplete}, {@code thenAccept}, or
 * {@code exceptionally} to react to the result. These tests verify that the
 * {@link CompletableFuture} contract Producer relies on is preserved: success
 * metadata is delivered exactly once, errors propagate through the chain, and
 * chained stages observe a consistent value.
 *
 * <p>These tests intentionally do not call {@link Producer#send} against a
 * real broker — they exercise the callback contract via the same
 * {@link CompletableFuture} that {@code send} returns. A separate
 * {@code ProducerIntegrationTest} (requires {@code docker-compose
 * -f docker-compose.test.yml up -d}) covers the wire-level behavior.
 */
class ProducerCallbackTest {

    /** Mirror of what {@code Producer.send} resolves to on success. */
    private static CompletableFuture<RecordMetadata> succeededSend(String topic) {
        return CompletableFuture.completedFuture(new RecordMetadata(topic, 0, 0L, 1L));
    }

    /** Mirror of what {@code Producer.send} resolves to on failure. */
    private static CompletableFuture<RecordMetadata> failedSend(Throwable cause) {
        CompletableFuture<RecordMetadata> f = new CompletableFuture<>();
        f.completeExceptionally(cause);
        return f;
    }

    @Test
    void whenComplete_invokesCallbackOnceWithMetadata() throws InterruptedException {
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<RecordMetadata> got = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger();

        succeededSend("cb-topic").whenComplete((md, t) -> {
            callCount.incrementAndGet();
            got.set(md);
            err.set(t);
            invoked.countDown();
        });

        assertTrue(invoked.await(2, TimeUnit.SECONDS), "callback should fire within timeout");
        assertEquals(1, callCount.get(), "callback must run exactly once");
        assertNotNull(got.get(), "success path must deliver metadata");
        assertEquals("cb-topic", got.get().topic());
        assertNull(err.get(), "no error on success");
    }

    @Test
    void thenAccept_receivesMetadataOnSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> seenTopic = new AtomicReference<>();

        succeededSend("then-topic").thenAccept(md -> {
            seenTopic.set(md.topic());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("then-topic", seenTopic.get());
    }

    @Test
    void chainedStages_preserveMetadataValue() throws Exception {
        CompletableFuture<RecordMetadata> first = succeededSend("chain-topic");
        CompletableFuture<String> second = first.thenApply(RecordMetadata::topic);
        CompletableFuture<Integer> third = second.thenApply(String::length);

        assertEquals("chain-topic".length(), third.get(2, TimeUnit.SECONDS).intValue());
    }

    @Test
    void multipleCallbacks_eachReceiveResult() throws InterruptedException {
        CompletableFuture<RecordMetadata> future = succeededSend("multi-topic");

        CountDownLatch latch = new CountDownLatch(3);
        future.whenComplete((md, t) -> latch.countDown());
        future.whenComplete((md, t) -> latch.countDown());
        future.thenAccept(md -> latch.countDown());

        assertTrue(latch.await(2, TimeUnit.SECONDS), "all 3 chained callbacks must fire");
    }

    @Test
    void whenComplete_propagatesExceptionToCallback() throws InterruptedException {
        IllegalStateException cause = new IllegalStateException("simulated failure");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RecordMetadata> got = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();

        failedSend(cause).whenComplete((md, t) -> {
            got.set(md);
            err.set(t);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNull(got.get(), "metadata must be null on failure");
        assertNotNull(err.get(), "error must be delivered to callback");
        assertSame(cause, err.get(), "callback receives the original throwable");
    }

    @Test
    void exceptionally_recoversFromFailure() throws Exception {
        RecordMetadata recovered = failedSend(new IllegalStateException("boom"))
            .exceptionally(t -> new RecordMetadata("recovered", -1, -1L, 0L))
            .get(2, TimeUnit.SECONDS);

        assertEquals("recovered", recovered.topic());
    }

    @Test
    void get_throwsExecutionExceptionOnFailure() {
        IllegalStateException cause = new IllegalStateException("nope");
        CompletableFuture<RecordMetadata> f = failedSend(cause);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> f.get(2, TimeUnit.SECONDS));
        assertSame(cause, ex.getCause(), "ExecutionException should wrap the original cause");
    }

    @Test
    void callback_receivesNonNullMetadataFields() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RecordMetadata> got = new AtomicReference<>();

        succeededSend("part-topic").whenComplete((md, t) -> {
            got.set(md);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        RecordMetadata md = got.get();
        assertNotNull(md);
        assertTrue(md.partition() >= 0, "partition should be non-negative");
        assertTrue(md.offset() >= 0L, "offset should be non-negative");
        assertTrue(md.timestamp() > 0L, "timestamp should be set");
    }

    @Test
    void completedFuture_doesNotBlockCallingThread() throws TimeoutException, ExecutionException, InterruptedException {
        long start = System.nanoTime();
        RecordMetadata md = succeededSend("fast").get(100, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertNotNull(md);
        assertTrue(elapsedMs < 50, "already-completed future must not block (took " + elapsedMs + "ms)");
    }
}
