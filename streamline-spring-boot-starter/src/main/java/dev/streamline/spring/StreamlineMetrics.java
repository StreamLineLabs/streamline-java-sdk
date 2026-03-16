package dev.streamline.spring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Micrometer metrics for Streamline operations.
 * Automatically registered when micrometer-core is on the classpath.
 */
public class StreamlineMetrics {

    private final Counter messagesProduced;
    private final Counter messagesConsumed;
    private final Counter errors;
    private final Timer produceLatency;
    private final Timer consumeLatency;

    public StreamlineMetrics(MeterRegistry registry) {
        this.messagesProduced = Counter.builder("streamline.messages.produced")
            .description("Total messages produced")
            .register(registry);
        this.messagesConsumed = Counter.builder("streamline.messages.consumed")
            .description("Total messages consumed")
            .register(registry);
        this.errors = Counter.builder("streamline.errors")
            .description("Total errors")
            .register(registry);
        this.produceLatency = Timer.builder("streamline.produce.latency")
            .description("Produce operation latency")
            .register(registry);
        this.consumeLatency = Timer.builder("streamline.consume.latency")
            .description("Consume operation latency")
            .register(registry);
    }

    public void recordProduce(Duration latency) {
        messagesProduced.increment();
        produceLatency.record(latency);
    }

    public void recordConsume(Duration latency) {
        messagesConsumed.increment();
        consumeLatency.record(latency);
    }

    public void recordError() {
        errors.increment();
    }
}
