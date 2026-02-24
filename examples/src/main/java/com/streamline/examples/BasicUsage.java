package com.streamline.examples;

import com.streamline.client.StreamlineClient;
import com.streamline.client.StreamlineConfig;
import com.streamline.client.producer.ProducerConfig;
import com.streamline.client.producer.ProducerRecord;
import com.streamline.client.consumer.ConsumerConfig;
import com.streamline.client.consumer.ConsumerRecord;

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
 * mvn compile exec:java -pl examples -Dexec.mainClass="com.streamline.examples.BasicUsage"
 * }</pre>
 */
public class BasicUsage {

    public static void main(String[] args) throws Exception {
        // Configure the client
        StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092"))
                .clientId("java-example")
                .build();

        try (StreamlineClient client = new StreamlineClient(config)) {
            // --- Produce Messages ---
            System.out.println("=== Producing Messages ===");

            // Simple produce
            client.produce("my-topic", "key-1", "Hello from Java SDK!");
            System.out.println("Produced message with key-1");

            // Produce with headers
            client.produce("my-topic", "key-2", "{\"event\":\"user_signup\",\"user\":\"alice\"}");
            System.out.println("Produced JSON message with key-2");

            // --- Consume Messages ---
            System.out.println("\n=== Consuming Messages ===");

            ConsumerConfig consumerConfig = ConsumerConfig.builder()
                    .groupId("java-example-group")
                    .autoOffsetReset("earliest")
                    .build();

            // Poll for messages
            List<ConsumerRecord<String, String>> records = client.poll(
                    "my-topic", consumerConfig, Duration.ofSeconds(5));

            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("Received: topic=%s, partition=%d, offset=%d, key=%s, value=%s%n",
                        record.topic(), record.partition(), record.offset(),
                        record.key(), record.value());
            }

            System.out.println("\nDone!");
        }
    }
}
