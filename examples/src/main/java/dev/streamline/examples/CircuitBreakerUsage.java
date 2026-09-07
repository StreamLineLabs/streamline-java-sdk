package dev.streamline.examples;

import dev.streamline.client.CircuitBreaker;
import dev.streamline.client.RecordMetadata;
import dev.streamline.client.Streamline;
import dev.streamline.client.StreamlineException;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;

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
 * mvn compile exec:java -pl examples -Dexec.mainClass="dev.streamline.examples.CircuitBreakerUsage"
 * }</pre>
 */
public class CircuitBreakerUsage {

    public static void main(String[] args) throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreaker.Config.builder()
                .failureThreshold(5)                  // Open after 5 consecutive failures
                .successThreshold(2)                  // Close after 2 successes in half-open
                .openTimeout(Duration.ofSeconds(30))  // Wait 30s before probing
                .halfOpenMaxRequests(3)               // Allow 3 probe requests in half-open
                .onStateChange((from, to) ->
                    System.out.printf("[Circuit Breaker] %s -> %s%n", from, to))
                .build()
        );

        ProducerConfig producerConfig = ProducerConfig.builder()
            .compressionType("lz4")
            .idempotent(true)
            .build();

        try (Streamline client = Streamline.builder()
                .bootstrapServers(ExampleEnv.bootstrapServers())
                .build();
             Producer<String, String> producer = client.createProducer(producerConfig)) {

            for (int i = 0; i < 20; i++) {
                final int index = i;
                try {
                    RecordMetadata result = breaker.execute(() ->
                        producer.send("events", "key-" + index,
                                "{\"event\":\"click\",\"i\":" + index + "}").join()
                    );
                    System.out.printf("Sent message %d to partition=%d, offset=%d%n",
                        index, result.partition(), result.offset());
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

            System.out.printf("%nFinal circuit state: %s%n", breaker.getState());
        }
    }
}
