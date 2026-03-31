package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP admin client for branched streams (M5 P1, Experimental).
 *
 * <p>Wraps the broker's {@code /api/v1/branches/*} admin API.
 */
public final class BranchAdminClient extends MoonshotHttpClient {

    public BranchAdminClient(MoonshotClientOptions opts) {
        super(opts);
    }

    /** A branch as returned by the admin API. */
    public record BranchView(
            String id,
            String parent,
            long createdAtMs,
            long messageCount,
            Map<String, Object> metadata) {}

    /** A message within a branch. */
    public record BranchMessage(String role, String text, long timestampMs) {}

    /** Optional fields for {@link #create}. */
    public record CreateOptions(String parent, Map<String, Object> metadata) {
        public static CreateOptions empty() { return new CreateOptions(null, null); }
    }

    public BranchView create(String topic, String name, CreateOptions opts) {
        requireNonEmpty(topic, "topic");
        requireNonEmpty(name, "name");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("name", name);
        if (opts != null && opts.parent() != null) body.put("parent", opts.parent());
        if (opts != null && opts.metadata() != null) body.put("metadata", opts.metadata());
        Response r = request("POST", "/api/v1/branches", body);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("POST", "/api/v1/branches", r.status, r.body);
        }
        return parseView(r.body);
    }

    public List<BranchView> list() {
        Response r = request("GET", "/api/v1/branches", null);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("GET", "/api/v1/branches", r.status, r.body);
        }
        return parseViewList(r.body, "items");
    }

    public BranchView get(String branchId) {
        requireNonEmpty(branchId, "branchId");
        String path = "/api/v1/branches/" + URLEncoder.encode(branchId, StandardCharsets.UTF_8);
        Response r = request("GET", path, null);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("GET", path, r.status, r.body);
        }
        return parseView(r.body);
    }

    public void delete(String branchId) {
        requireNonEmpty(branchId, "branchId");
        String path = "/api/v1/branches/" + URLEncoder.encode(branchId, StandardCharsets.UTF_8);
        Response r = request("DELETE", path, null);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("DELETE", path, r.status, r.body);
        }
    }

    public void append(String branchId, String role, String text, long timestampMs) {
        requireNonEmpty(branchId, "branchId");
        requireNonEmpty(role, "role");
        String path = "/api/v1/branches/" + URLEncoder.encode(branchId, StandardCharsets.UTF_8) + "/messages";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", role);
        body.put("text", text == null ? "" : text);
        if (timestampMs != 0) body.put("timestamp_ms", timestampMs);
        Response r = request("POST", path, body);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("POST", path, r.status, r.body);
        }
    }

    public List<BranchMessage> messages(String branchId) {
        requireNonEmpty(branchId, "branchId");
        String path = "/api/v1/branches/" + URLEncoder.encode(branchId, StandardCharsets.UTF_8) + "/messages";
        Response r = request("GET", path, null);
        if (r.status < 200 || r.status >= 300) {
            throw new HttpException("GET", path, r.status, r.body);
        }
        return parseMessageList(r.body);
    }

    private static BranchView parseView(String body) {
        try {
            JsonNode n = JSON.readTree(body == null || body.isEmpty() ? "{}" : body);
            return toView(n);
        } catch (Exception e) {
            throw new HttpException("decode", "branch", 0, body);
        }
    }

    private static BranchView toView(JsonNode n) {
        Map<String, Object> meta = new HashMap<>();
        JsonNode m = n.get("metadata");
        if (m != null && m.isObject()) {
            m.fields().forEachRemaining(e -> meta.put(e.getKey(), e.getValue().isValueNode()
                    ? (e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText())
                    : e.getValue()));
        }
        return new BranchView(
                n.path("id").asText(""),
                n.has("parent") && !n.get("parent").isNull() ? n.get("parent").asText() : null,
                n.path("created_at_ms").asLong(0),
                n.path("message_count").asLong(0),
                meta);
    }

    private static List<BranchView> parseViewList(String body, String wrapKey) {
        try {
            JsonNode n = JSON.readTree(body == null || body.isEmpty() ? "[]" : body);
            JsonNode arr = n.isArray() ? n : n.path(wrapKey);
            List<BranchView> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode item : arr) out.add(toView(item));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<BranchMessage> parseMessageList(String body) {
        try {
            JsonNode n = JSON.readTree(body == null || body.isEmpty() ? "[]" : body);
            JsonNode arr = n.isArray() ? n : n.path("messages");
            List<BranchMessage> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    out.add(new BranchMessage(
                            item.path("role").asText(""),
                            item.path("text").asText(""),
                            item.path("timestamp_ms").asLong(0)));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // hush IDE: ObjectNode is imported only to make creation explicit elsewhere
    @SuppressWarnings("unused")
    private static ObjectNode mapper() { return JSON.createObjectNode(); }
}
