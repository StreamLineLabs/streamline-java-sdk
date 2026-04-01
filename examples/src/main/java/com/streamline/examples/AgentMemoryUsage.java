package com.streamline.examples;

import com.streamline.client.StreamlineClient;
import com.streamline.client.StreamlineConfig;
import com.streamline.client.memory.MemoryEntry;
import com.streamline.client.memory.MemoryHit;

import java.util.List;

/**
 * Agent Memory example demonstrating remember/recall with semantic search.
 *
 * <p>Shows single-agent memory and multi-agent shared memory via namespaces.
 *
 * <pre>{@code
 * # Start Streamline with memory features enabled
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples \
 *   -Dexec.mainClass="com.streamline.examples.AgentMemoryUsage"
 * }</pre>
 */
public class AgentMemoryUsage {

    public static void main(String[] args) throws Exception {
        String brokers = System.getenv().getOrDefault(
                "STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092");
        String httpEndpoint = System.getenv().getOrDefault(
                "STREAMLINE_HTTP", "http://localhost:9094");

        StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(brokers)
                .httpEndpoint(httpEndpoint)
                .clientId("agent-memory-example")
                .build();

        try (StreamlineClient client = StreamlineClient.create(config)) {
            singleAgentMemory(client);
            multiAgentSharedMemory(client);
        }

        System.out.println("\nDone!");
    }

    private static void singleAgentMemory(StreamlineClient client) throws Exception {
        System.out.println("=== Single Agent Memory ===");

        // Store architectural decisions
        client.memoryRemember(MemoryEntry.builder()
                .agentId("demo-agent")
                .content("We chose PostgreSQL for its JSONB support and mature ecosystem")
                .kind("fact")
                .importance(0.8)
                .tags(List.of("architecture", "database"))
                .build());

        client.memoryRemember(MemoryEntry.builder()
                .agentId("demo-agent")
                .content("Redis is used as a caching layer with a 15-minute TTL")
                .kind("fact")
                .importance(0.7)
                .tags(List.of("architecture", "caching"))
                .build());

        client.memoryRemember(MemoryEntry.builder()
                .agentId("demo-agent")
                .content("User requested dark mode support in the dashboard")
                .kind("preference")
                .importance(0.6)
                .tags(List.of("ui", "user-request"))
                .build());

        System.out.println("Stored 3 memories\n");

        // Recall by semantic similarity
        System.out.println("--- Recall: 'why did we pick our database?' ---");
        List<MemoryHit> results = client.memoryRecall(
                "demo-agent", "why did we pick our database?", 5);
        for (MemoryHit hit : results) {
            System.out.printf("  [%s] score=%.2f: %s%n",
                    hit.tier(), hit.score(), hit.content());
        }

        System.out.println("\n--- Recall: 'caching strategy' ---");
        results = client.memoryRecall("demo-agent", "caching strategy", 5);
        for (MemoryHit hit : results) {
            System.out.printf("  [%s] score=%.2f: %s%n",
                    hit.tier(), hit.score(), hit.content());
        }
    }

    private static void multiAgentSharedMemory(StreamlineClient client) throws Exception {
        System.out.println("\n=== Multi-Agent Shared Memory ===");

        // Agent A stores a decision in the shared namespace
        client.memoryRemember(MemoryEntry.builder()
                .agentId("agent-a")
                .namespace("team-shared")
                .content("Deploy target is Kubernetes on AWS EKS")
                .kind("fact")
                .importance(0.9)
                .tags(List.of("infra", "deployment"))
                .build());
        System.out.println("Agent A stored deployment decision");

        // Agent B stores related context in the same namespace
        client.memoryRemember(MemoryEntry.builder()
                .agentId("agent-b")
                .namespace("team-shared")
                .content("CI/CD pipeline uses GitHub Actions with OIDC auth to AWS")
                .kind("fact")
                .importance(0.8)
                .tags(List.of("infra", "ci-cd"))
                .build());
        System.out.println("Agent B stored CI/CD context");

        // Agent C recalls shared memories from the team namespace
        System.out.println("\n--- Agent C recalls 'deployment infrastructure' from shared namespace ---");
        List<MemoryHit> results = client.memoryRecall(
                "agent-c", "team-shared", "deployment infrastructure", 5);
        for (MemoryHit hit : results) {
            System.out.printf("  [%s] score=%.2f: %s%n",
                    hit.tier(), hit.score(), hit.content());
        }
    }
}
