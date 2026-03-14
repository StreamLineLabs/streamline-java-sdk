package com.streamline.examples;

import com.streamline.client.Streamline;

/**
 * Demonstrates using Streamline's embedded SQL analytics engine.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Streamline server running with {@code --playground} or via Docker</li>
 *   <li>Topic "events" with some messages</li>
 * </ul>
 *
 * <p>Run:
 * <pre>mvn compile exec:java -Dexec.mainClass=com.streamline.examples.QueryUsage</pre>
 */
public class QueryUsage {

    public static void main(String[] args) {
        String bootstrap = System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP", "localhost:9092");
        String httpUrl = System.getenv().getOrDefault("STREAMLINE_HTTP", "http://localhost:9094");

        try (Streamline client = Streamline.builder().bootstrapServers(bootstrap).build()) {
            // Create topic and produce sample data
            System.out.println("Producing sample events...");
            for (int i = 0; i < 10; i++) {
                client.produce("events", "key-" + i,
                        String.format("{\"user\":\"user-%d\",\"action\":\"click\",\"value\":%d}", i, i * 10));
            }
            System.out.println("Produced 10 events");

            // Query: Select all events
            System.out.println("\n--- All events (LIMIT 5) ---");
            String result = client.query("SELECT * FROM topic('events') LIMIT 5");
            System.out.println(result);

            // Query: Aggregation
            System.out.println("\n--- Count by action ---");
            result = client.query("SELECT action, COUNT(*) as cnt FROM topic('events') GROUP BY action");
            System.out.println(result);

            // Query: Filtering
            System.out.println("\n--- Events with value > 50 ---");
            result = client.query("SELECT * FROM topic('events') WHERE value > 50");
            System.out.println(result);

        } catch (Exception e) {
            System.err.println("Query failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
