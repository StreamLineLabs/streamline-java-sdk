package dev.streamline.examples;

import dev.streamline.client.Streamline;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.query.QueryClient;

/**
 * Demonstrates using Streamline's embedded SQL analytics engine.
 *
 * Prerequisites:
 *   - Streamline server running (docker run -d -p 9092:9092 -p 9094:9094 ghcr.io/streamlinelabs/streamline:latest)
 *   - Topic "events" with some messages
 *
 * Run:
 *   mvn compile exec:java -Dexec.mainClass=dev.streamline.examples.QueryUsage
 */
public class QueryUsage {

    public static void main(String[] args) {
        String httpUrl = System.getenv().getOrDefault("STREAMLINE_HTTP", "http://localhost:9094");

        try {
            QueryClient queryClient = new QueryClient(httpUrl);

            // First, produce some sample data
            String bootstrap = System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP", "localhost:9092");
            try (Streamline client = Streamline.builder().bootstrapServers(bootstrap).build()) {
                client.createTopic("events", 1);
                for (int i = 0; i < 10; i++) {
                    client.produce("events", "key-" + i,
                            String.format("{\"user\":\"user-%d\",\"action\":\"click\",\"value\":%d}", i, i * 10));
                }
                System.out.println("Produced 10 events");
            }

            // Query: Select all events
            System.out.println("\n--- All events ---");
            String result = queryClient.query("SELECT * FROM topic('events') LIMIT 5");
            System.out.println(result);

            // Query: Aggregation
            System.out.println("\n--- Count by action ---");
            result = queryClient.query("SELECT action, COUNT(*) as cnt FROM topic('events') GROUP BY action");
            System.out.println(result);

            // Query with options (custom timeout and max rows)
            System.out.println("\n--- With options ---");
            result = queryClient.query("SELECT * FROM topic('events') ORDER BY offset DESC", 5000, 3);
            System.out.println(result);

            // Explain query plan
            System.out.println("\n--- Query plan ---");
            String plan = queryClient.explain("SELECT * FROM topic('events') WHERE value > 50");
            System.out.println(plan);

        } catch (Exception e) {
            System.err.println("Query failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
