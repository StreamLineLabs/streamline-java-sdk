package dev.streamline.examples;

import dev.streamline.client.Streamline;
import dev.streamline.client.query.QueryClient;

/**
 * Demonstrates using Streamline's embedded SQL analytics engine over the HTTP API.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Streamline server running with {@code --playground} or via Docker</li>
 *   <li>Topic "events" with some messages</li>
 * </ul>
 *
 * <pre>{@code
 * mvn compile exec:java -pl examples -Dexec.mainClass="dev.streamline.examples.QueryUsage"
 * }</pre>
 */
public class QueryUsage {

    public static void main(String[] args) throws Exception {
        try (Streamline client = Streamline.builder()
                .bootstrapServers(ExampleEnv.bootstrapServers())
                .httpEndpoint(ExampleEnv.httpUrl())
                .build()) {

            System.out.println("Producing sample events...");
            for (int i = 0; i < 10; i++) {
                client.produce("events", "key-" + i,
                        String.format("{\"user\":\"user-%d\",\"action\":\"click\",\"value\":%d}", i, i * 10));
            }
            System.out.println("Produced 10 events");

            QueryClient queries = new QueryClient(ExampleEnv.httpUrl());

            System.out.println("\n--- All events (LIMIT 5) ---");
            System.out.println(queries.query("SELECT * FROM topic('events') LIMIT 5"));

            System.out.println("\n--- Count by action ---");
            System.out.println(queries.query(
                    "SELECT action, COUNT(*) as cnt FROM topic('events') GROUP BY action"));

            System.out.println("\n--- Events with value > 50 ---");
            System.out.println(queries.query("SELECT * FROM topic('events') WHERE value > 50"));

            System.out.println("\n--- Query plan ---");
            System.out.println(queries.explain("SELECT * FROM topic('events') WHERE value > 50"));
        }
    }
}
