package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contracts validate client (M4, Experimental).
 *
 * <p>Wraps {@code POST /api/v1/contracts/validate}.
 */
public final class ContractsClient extends MoonshotHttpClient {

    public ContractsClient(MoonshotClientOptions opts) {
        super(opts);
    }

    public record ValidationFailure(String fieldPath, String expected, String actual, String message) {}

    public record ValidationResult(boolean valid, Integer schemaId, List<ValidationFailure> errors) {}

    /**
     * Dry-run a contract against a value.
     *
     * @param contract the contract definition (sent as JSON object)
     * @param value    the value to validate; may be a Map / List / String / byte[] /
     *                 any JSON-serializable value. byte[] and String are sent as
     *                 {@code value_string}; everything else as {@code value}.
     */
    public ValidationResult validate(Map<String, Object> contract, Object value) {
        Objects.requireNonNull(contract, "contract");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", contract);
        if (value instanceof byte[] bytes) {
            body.put("value_string", new String(bytes, StandardCharsets.UTF_8));
        } else if (value instanceof String s) {
            body.put("value_string", s);
        } else {
            body.put("value", value);
        }

        Response r = request("POST", "/api/v1/contracts/validate", body);
        if (r.status == 200) {
            Integer sid = parseSchemaId(r.body);
            return new ValidationResult(true, sid, List.of());
        }
        if (r.status == 400) {
            Integer sid = parseSchemaId(r.body);
            return new ValidationResult(false, sid, parseErrors(r.body));
        }
        throw new HttpException("POST", "/api/v1/contracts/validate", r.status, r.body);
    }

    private static Integer parseSchemaId(String body) {
        try {
            if (body == null || body.isEmpty()) return null;
            JsonNode n = JSON.readTree(body);
            JsonNode sid = n.get("schema_id");
            if (sid == null || sid.isNull()) return null;
            return sid.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<ValidationFailure> parseErrors(String body) {
        List<ValidationFailure> out = new ArrayList<>();
        try {
            if (body == null || body.isEmpty()) return out;
            JsonNode n = JSON.readTree(body);
            JsonNode errs = n.get("errors");
            if (errs != null && errs.isArray()) {
                for (JsonNode e : errs) {
                    out.add(new ValidationFailure(
                            e.path("field_path").asText(""),
                            e.path("expected").asText(""),
                            e.path("actual").asText(""),
                            e.path("message").asText("")));
                }
            }
        } catch (Exception ignored) {
            // fall through with whatever was parsed
        }
        return out;
    }
}
