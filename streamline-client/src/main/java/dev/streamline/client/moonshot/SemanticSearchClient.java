package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;
import dev.streamline.client.http.UriEncoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP client for semantic-topic search (M2 P1, Experimental).
 *
 * <p>Wraps {@code POST /api/v1/topics/{topic}/search}.
 */
public final class SemanticSearchClient extends MoonshotHttpClient {

    public SemanticSearchClient(MoonshotClientOptions opts) {
        super(opts);
    }

    public record Hit(int partition, long offset, double score, String value) {}

    public record SearchResult(List<Hit> hits, int tookMs) {}

    public record SearchOptions(int k, Map<String, Object> filter) {
        public static SearchOptions defaults() { return new SearchOptions(10, null); }
    }

    public SearchResult search(String topic, String query, SearchOptions opts) {
        requireNonEmpty(topic, "topic");
        requireNonEmpty(query, "query");
        Objects.requireNonNull(opts, "opts");
        int k = opts.k() == 0 ? 10 : opts.k();
        if (k <= 0 || k > 1000) {
            throw new IllegalArgumentException("k must be in [1, 1000]");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("k", k);
        if (opts.filter() != null) body.put("filter", opts.filter());

        String path = "/api/v1/topics/" + UriEncoder.encodePathSegment(topic) + "/search";
        Response r = request("POST", path, body);
        if (r.status >= 400) {
            throw new HttpException("POST", path, r.status, r.body);
        }
        return parse(r.body);
    }

    private static SearchResult parse(String body) {
        try {
            JsonNode n = JSON.readTree(body == null || body.isEmpty() ? "{}" : body);
            List<Hit> hits = new ArrayList<>();
            JsonNode arr = n.path("hits");
            if (arr.isArray()) {
                for (JsonNode h : arr) {
                    String value = h.has("value") && !h.get("value").isNull() ? h.get("value").asText() : null;
                    hits.add(new Hit(
                            h.path("partition").asInt(0),
                            h.path("offset").asLong(0),
                            h.path("score").asDouble(0.0),
                            value));
                }
            }
            return new SearchResult(hits, n.path("took_ms").asInt(0));
        } catch (Exception e) {
            return new SearchResult(List.of(), 0);
        }
    }
}
