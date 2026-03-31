package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP client for the Agent Memory Fabric (M1 P1, Experimental).
 *
 * <p>Wraps {@code POST /api/v1/memory/remember} and
 * {@code POST /api/v1/memory/recall}.
 */
public final class MemoryClient extends MoonshotHttpClient {

    public enum Kind {
        OBSERVATION("observation"),
        FACT("fact"),
        PROCEDURE("procedure");

        private final String wire;
        Kind(String wire) { this.wire = wire; }
        public String wire() { return wire; }
    }

    private static final Set<String> VALID_KINDS = Set.of("observation", "fact", "procedure");

    public MemoryClient(MoonshotClientOptions opts) {
        super(opts);
    }

    public record WrittenEntry(String topic, long offset) {}

    public record RecalledMemory(String tier, String topic, long offset, String content, double score) {}

    public record RememberParams(
            String agentId,
            Kind kind,
            String content,
            double importance,
            List<String> tags,
            String skill) {}

    public record RecallParams(String agentId, String query, int k, int minHits) {}

    public List<WrittenEntry> remember(RememberParams p) {
        Objects.requireNonNull(p, "params");
        requireNonEmpty(p.agentId(), "agentId");
        requireNonEmpty(p.content(), "content");
        if (p.kind() == null || !VALID_KINDS.contains(p.kind().wire())) {
            throw new IllegalArgumentException("invalid kind: " + p.kind());
        }
        double importance = p.importance() == 0.0 ? 0.5 : p.importance();
        if (importance < 0.0 || importance > 1.0) {
            throw new IllegalArgumentException("importance must be in [0.0, 1.0]");
        }
        if (p.kind() == Kind.PROCEDURE && (p.skill() == null || p.skill().isEmpty())) {
            throw new IllegalArgumentException("skill is required for kind=procedure");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", p.agentId());
        body.put("kind", p.kind().wire());
        body.put("content", p.content());
        body.put("importance", importance);
        body.put("tags", p.tags() == null ? List.of() : p.tags());
        if (p.kind() == Kind.PROCEDURE) body.put("skill", p.skill());

        Response r = request("POST", "/api/v1/memory/remember", body);
        if (r.status >= 400) {
            throw new HttpException("POST", "/api/v1/memory/remember", r.status, r.body);
        }
        return parseWritten(r.body);
    }

    public List<RecalledMemory> recall(RecallParams p) {
        Objects.requireNonNull(p, "params");
        requireNonEmpty(p.agentId(), "agentId");
        requireNonEmpty(p.query(), "query");
        int k = p.k() == 0 ? 10 : p.k();
        if (k <= 0 || k > 1000) {
            throw new IllegalArgumentException("k must be in [1, 1000]");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", p.agentId());
        body.put("query", p.query());
        body.put("k", k);
        body.put("min_hits", p.minHits());

        Response r = request("POST", "/api/v1/memory/recall", body);
        if (r.status >= 400) {
            throw new HttpException("POST", "/api/v1/memory/recall", r.status, r.body);
        }
        return parseRecalled(r.body);
    }

    private static List<WrittenEntry> parseWritten(String body) {
        List<WrittenEntry> out = new ArrayList<>();
        try {
            if (body == null || body.isEmpty()) return out;
            JsonNode n = JSON.readTree(body);
            JsonNode arr = n.path("written");
            if (arr.isArray()) {
                for (JsonNode e : arr) {
                    out.add(new WrittenEntry(e.path("topic").asText(""), e.path("offset").asLong(0)));
                }
            }
        } catch (Exception ignored) {
            // tolerate partial / malformed responses
        }
        return out;
    }

    private static List<RecalledMemory> parseRecalled(String body) {
        List<RecalledMemory> out = new ArrayList<>();
        try {
            if (body == null || body.isEmpty()) return out;
            JsonNode n = JSON.readTree(body);
            JsonNode arr = n.path("hits");
            if (arr.isArray()) {
                for (JsonNode h : arr) {
                    out.add(new RecalledMemory(
                            h.path("tier").asText(""),
                            h.path("topic").asText(""),
                            h.path("offset").asLong(0),
                            h.path("content").asText(""),
                            h.path("score").asDouble(0.0)));
                }
            }
        } catch (Exception ignored) {
            // tolerate partial / malformed responses
        }
        return out;
    }
}
