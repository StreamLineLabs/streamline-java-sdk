package com.streamline.examples;

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
 * mvn compile exec:java -pl examples -Dexec.mainClass="com.streamline.examples.AdminClientUsage"
 * }</pre>
 */
public class AdminClientUsage {

    public static void main(String[] args) throws Exception {
        String servers = System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092");

        Streamline client = Streamline.builder()
            .bootstrapServers(servers)
            .clientId("java-admin-example")
            .build();

        try (AdminClient admin = client.admin()) {
            // --- Topic Management ---
            System.out.println("=== Topic Management ===");
            admin.createTopic("events", 3, (short) 1);
            System.out.println("Created topic 'events' with 3 partitions");

            var topics = admin.listTopics();
            System.out.println("Topics: " + topics);

            var description = admin.describeTopic("events");
            System.out.println("Topic details: " + description);

            // --- Consumer Groups ---
            System.out.println("\n=== Consumer Groups ===");
            var groups = admin.listConsumerGroups();
            System.out.println("Consumer groups: " + groups);

            // --- Cluster Info ---
            System.out.println("\n=== Cluster Info ===");
            var nodes = admin.describeCluster();
            System.out.println("Cluster nodes: " + nodes);

            // --- Cleanup ---
            admin.deleteTopic("events");
            System.out.println("\nCleaned up topic 'events'");
        } finally {
            client.close();
        }
    }
}
