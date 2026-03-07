package com.streamline.examples;

import dev.streamline.client.CircuitBreaker;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.StreamlineException;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.producer.RecordMetadata;

import java.time.Duration;

/**
 * Demonstrates the circuit breaker pattern for resilient message production.
 *
 * <p>The circuit breaker prevents your application from repeatedly attempting
 * operations against a failing server. After a configurable number of consecutive
 * failures, it "opens" and rejects requests immediately, giving the server
 * time to recover.
 *
 * <pre>{@code
 * # Start Streamline
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples -Dexec.mainClass="com.streamline.examples.CircuitBreakerUsage"
 * }</pre>
 */
public class CircuitBreakerUsage {

    public static void main(String[] args) throws Exception {
        String servers = System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092");

        // Configure the circuit breaker
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreaker.Config.builder()
                .failureThreshold(5)         // Open after 5 consecutive failures
                .successThreshold(2)         // Close after 2 successes in half-open
                .openTimeout(Duration.ofSeconds(30))  // Wait 30s before probing
                .halfOpenMaxRequests(3)      // Allow 3 probe requests in half-open
                .onStateChange((from, to) ->
                    System.out.printf("[Circuit Breaker] %s → %s%n", from, to))
                .build()
        );

        StreamlineConfig config = StreamlineConfig.builder()
            .bootstrapServers(servers)
            .build();

        ProducerConfig producerConfig = ProducerConfig.builder()
            .compressionType("lz4")
            .idempotent(true)
            .build();

        try (var client = new dev.streamline.client.Streamline(config);
             Producer<String, String> producer = client.createProducer(producerConfig)) {

            // Send messages through the circuit breaker
            for (int i = 0; i < 20; i++) {
                try {
                    RecordMetadata result = breaker.execute(() ->
                        producer.send("events", "key-" + i, "{\"event\":\"click\",\"i\":" + i + "}").join()
                    );
                    System.out.printf("Sent message %d to partition=%d, offset=%d%n",
                        i, result.partition(), result.offset());
                } catch (StreamlineException e) {
                    if (e.isRetryable()) {
                        System.out.printf("Retryable error (circuit state: %s): %s%n",
                            breaker.getState(), e.getMessage());
                        Thread.sleep(1000); // back off
                    } else {
                        System.err.printf("Non-retryable error: %s%n", e.getMessage());
                        break;
                    }
                }
            }

            // Check final state
            System.out.printf("%nFinal circuit state: %s%n", breaker.getState());

        }
    }
}
