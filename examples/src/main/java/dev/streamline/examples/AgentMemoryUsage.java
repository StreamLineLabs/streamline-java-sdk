package dev.streamline.examples;

import dev.streamline.client.moonshot.MemoryClient;
import dev.streamline.client.moonshot.MoonshotClientOptions;

import java.util.List;

/**
 * Agent Memory example demonstrating remember/recall with semantic search.
 *
 * <p>Memory is served by the broker's HTTP API, so only the HTTP endpoint is needed.
 *
 * <pre>{@code
 * # Start Streamline with memory features enabled
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples \
 *   -Dexec.mainClass="dev.streamline.examples.AgentMemoryUsage"
 * }</pre>
 */
public class AgentMemoryUsage {

    public static void main(String[] args) {
        MemoryClient memory = new MemoryClient(new MoonshotClientOptions(ExampleEnv.httpUrl()));

        singleAgentMemory(memory);
        multiAgentSharedMemory(memory);

        System.out.println("\nDone!");
    }

    private static void singleAgentMemory(MemoryClient memory) {
        System.out.println("=== Single Agent Memory ===");

        remember(memory, "demo-agent", MemoryClient.Kind.FACT,
                "We chose PostgreSQL for its JSONB support and mature ecosystem",
                0.8, List.of("architecture", "database"));
        remember(memory, "demo-agent", MemoryClient.Kind.FACT,
                "Redis is used as a caching layer with a 15-minute TTL",
                0.7, List.of("architecture", "caching"));
        remember(memory, "demo-agent", MemoryClient.Kind.OBSERVATION,
                "User requested dark mode support in the dashboard",
                0.6, List.of("ui", "user-request"));

        System.out.println("Stored 3 memories\n");

        recall(memory, "demo-agent", "why did we pick our database?");
        recall(memory, "demo-agent", "caching strategy");
    }

    private static void multiAgentSharedMemory(MemoryClient memory) {
        System.out.println("\n=== Multi-Agent Shared Memory ===");

        // Agents share knowledge by writing under the same skill.
        remember(memory, "agent-a", MemoryClient.Kind.FACT,
                "Deploy target is Kubernetes on AWS EKS",
                0.9, List.of("infra", "deployment"), "team-shared");
        System.out.println("Agent A stored deployment decision");

        remember(memory, "agent-b", MemoryClient.Kind.FACT,
                "CI/CD pipeline uses GitHub Actions with OIDC auth to AWS",
                0.8, List.of("infra", "ci-cd"), "team-shared");
        System.out.println("Agent B stored CI/CD context");

        recall(memory, "agent-a", "deployment infrastructure");
    }

    private static void remember(MemoryClient memory, String agentId, MemoryClient.Kind kind,
                                 String content, double importance, List<String> tags) {
        remember(memory, agentId, kind, content, importance, tags, null);
    }

    private static void remember(MemoryClient memory, String agentId, MemoryClient.Kind kind,
                                 String content, double importance, List<String> tags, String skill) {
        List<MemoryClient.WrittenEntry> written = memory.remember(
                new MemoryClient.RememberParams(agentId, kind, content, importance, tags, skill));
        for (MemoryClient.WrittenEntry entry : written) {
            System.out.printf("  wrote %s@%d%n", entry.topic(), entry.offset());
        }
    }

    private static void recall(MemoryClient memory, String agentId, String query) {
        System.out.printf("%n--- Recall: '%s' ---%n", query);
        List<MemoryClient.RecalledMemory> hits =
                memory.recall(new MemoryClient.RecallParams(agentId, query, 5, 1));
        for (MemoryClient.RecalledMemory hit : hits) {
            System.out.printf("  [%s] score=%.2f: %s%n", hit.tier(), hit.score(), hit.content());
        }
    }
}
