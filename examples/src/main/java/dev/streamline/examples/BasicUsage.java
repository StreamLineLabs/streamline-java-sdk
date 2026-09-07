package dev.streamline.examples;

import dev.streamline.client.RecordMetadata;
import dev.streamline.client.Streamline;
import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.consumer.ConsumerRecord;

import java.time.Duration;
import java.util.List;

/**
 * Basic example demonstrating Streamline Java SDK usage.
 *
 * <p>Ensure a Streamline server is running at localhost:9092 before running.
 *
 * <pre>{@code
 * # Start Streamline
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples -Dexec.mainClass="dev.streamline.examples.BasicUsage"
 * }</pre>
 */
public class BasicUsage {

    public static void main(String[] args) throws Exception {
        try (Streamline client = Streamline.builder()
                .bootstrapServers(ExampleEnv.bootstrapServers())
                .build()) {

            // --- Produce Messages ---
            System.out.println("=== Producing Messages ===");

            RecordMetadata first = client.produce("my-topic", "key-1", "Hello from Java SDK!");
            System.out.printf("Produced key-1 to partition=%d offset=%d%n",
                    first.partition(), first.offset());

            client.produce("my-topic", "key-2", "{\"event\":\"user_signup\",\"user\":\"alice\"}");
            System.out.println("Produced JSON message with key-2");

            // Fire-and-forget with a future
            client.produceAsync("my-topic", "key-3", "async message").join();
            System.out.println("Produced key-3 asynchronously");

            // --- Consume Messages ---
            System.out.println("\n=== Consuming Messages ===");

            ConsumerConfig consumerConfig = ConsumerConfig.builder()
                    .groupId("java-example-group")
                    .autoOffsetReset("earliest")
                    .build();

            try (Consumer<String, String> consumer =
                         client.consumer("my-topic", consumerConfig)) {
                consumer.subscribe();

                List<ConsumerRecord<String, String>> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("Received: topic=%s, partition=%d, offset=%d, key=%s, value=%s%n",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), record.value());
                }

                consumer.commitSync();
            }

            System.out.println("\nDone!");
        }
    }
}
