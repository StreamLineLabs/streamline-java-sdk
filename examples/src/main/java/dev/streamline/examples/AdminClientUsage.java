package dev.streamline.examples;

import dev.streamline.client.Streamline;
import dev.streamline.client.admin.AdminClient;

/**
 * Demonstrates AdminClient usage for topic management,
 * consumer group inspection, and cluster operations.
 *
 * <p>Ensure a Streamline server is running at localhost:9092 before running.
 *
 * <pre>{@code
 * # Start Streamline
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples -Dexec.mainClass="dev.streamline.examples.AdminClientUsage"
 * }</pre>
 */
public class AdminClientUsage {

    public static void main(String[] args) throws Exception {
        try (Streamline client = Streamline.builder()
                .bootstrapServers(ExampleEnv.bootstrapServers())
                .httpEndpoint(ExampleEnv.httpUrl())
                .build();
             AdminClient admin = client.admin()) {

            // --- Topic Management ---
            System.out.println("=== Topic Management ===");
            admin.createTopic("events", 3, (short) 1);
            System.out.println("Created topic 'events' with 3 partitions");

            System.out.println("Topics: " + admin.listTopics());
            System.out.println("Topic details: " + admin.describeTopic("events"));

            // --- Consumer Groups ---
            System.out.println("\n=== Consumer Groups ===");
            System.out.println("Consumer groups: " + admin.listConsumerGroups());

            // --- Cluster Info ---
            System.out.println("\n=== Cluster Info ===");
            System.out.println("Cluster nodes: " + admin.describeCluster());

            // --- Cleanup ---
            admin.deleteTopic("events");
            System.out.println("\nCleaned up topic 'events'");
        }
    }
}
