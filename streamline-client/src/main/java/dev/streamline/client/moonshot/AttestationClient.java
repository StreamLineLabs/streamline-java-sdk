package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP attestation client (M4, Experimental).
 *
 * <p>Wraps {@code POST /api/v1/attest} and {@code POST /api/v1/attest/verify}.
 * Pairs with {@link io.streamline.attestation.AttestationVerifier} for offline
 * verification using a public key.
 */
public final class AttestationClient extends MoonshotHttpClient {

    public static final String HEADER = "streamline-attest";

    private final String defaultKeyId;
    private final String defaultAlgorithm;

    public AttestationClient(MoonshotClientOptions opts) {
        this(opts, "broker-0", "ed25519");
    }

    public AttestationClient(MoonshotClientOptions opts, String keyId, String algorithm) {
        super(opts);
        this.defaultKeyId = keyId == null || keyId.isEmpty() ? "broker-0" : keyId;
        this.defaultAlgorithm = algorithm == null || algorithm.isEmpty() ? "ed25519" : algorithm;
    }

    public record SignedAttestation(
            String keyId, String algorithm, long timestampMs,
            String payloadSha256, String signatureB64,
            String headerName, String headerValue) {}

    public record SignParams(
            String topic, int partition, long offset,
            byte[] valueBytes, String valueString,
            int schemaId, long timestampMs, String keyId) {}

    public record VerifyParams(
            String topic, int partition, long offset,
            byte[] valueBytes, String valueString,
            int schemaId, long timestampMs,
            String signatureB64, String keyId, String algorithm) {}

    public SignedAttestation sign(SignParams p) {
        Objects.requireNonNull(p, "params");
        requireNonEmpty(p.topic(), "topic");
        if (p.valueBytes() != null && p.valueString() != null && !p.valueString().isEmpty()) {
            throw new IllegalArgumentException("set valueBytes or valueString, not both");
        }
        long ts = p.timestampMs() == 0 ? System.currentTimeMillis() : p.timestampMs();
        String keyId = (p.keyId() == null || p.keyId().isEmpty()) ? defaultKeyId : p.keyId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", p.topic());
        body.put("partition", p.partition());
        body.put("offset", p.offset());
        body.put("schema_id", p.schemaId());
        body.put("timestamp_ms", ts);
        body.put("key_id", keyId);
        if (p.valueBytes() != null) {
            body.put("value_b64", Base64.getEncoder().encodeToString(p.valueBytes()));
        } else {
            body.put("value", p.valueString() == null ? "" : p.valueString());
        }

        Response r = request("POST", "/api/v1/attest", body);
        if (r.status != 200) {
            throw new HttpException("POST", "/api/v1/attest", r.status, r.body);
        }
        return parseSigned(r.body);
    }

    public boolean verify(VerifyParams p) {
        Objects.requireNonNull(p, "params");
        requireNonEmpty(p.topic(), "topic");
        requireNonEmpty(p.signatureB64(), "signatureB64");
        String keyId = (p.keyId() == null || p.keyId().isEmpty()) ? defaultKeyId : p.keyId();
        String algo = (p.algorithm() == null || p.algorithm().isEmpty()) ? defaultAlgorithm : p.algorithm();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", p.topic());
        body.put("partition", p.partition());
        body.put("offset", p.offset());
        body.put("schema_id", p.schemaId());
        body.put("timestamp_ms", p.timestampMs());
        body.put("key_id", keyId);
        body.put("signature_b64", p.signatureB64());
        body.put("algorithm", algo);
        if (p.valueBytes() != null) {
            body.put("value_b64", Base64.getEncoder().encodeToString(p.valueBytes()));
        } else {
            body.put("value", p.valueString() == null ? "" : p.valueString());
        }

        Response r = request("POST", "/api/v1/attest/verify", body);
        if (r.status != 200) {
            throw new HttpException("POST", "/api/v1/attest/verify", r.status, r.body);
        }
        try {
            JsonNode n = JSON.readTree(r.body == null || r.body.isEmpty() ? "{}" : r.body);
            return n.path("valid").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static SignedAttestation parseSigned(String body) {
        try {
            JsonNode n = JSON.readTree(body == null || body.isEmpty() ? "{}" : body);
            return new SignedAttestation(
                    n.path("key_id").asText(""),
                    n.path("algorithm").asText(""),
                    n.path("timestamp_ms").asLong(0),
                    n.path("payload_sha256").asText(""),
                    n.path("signature_b64").asText(""),
                    n.path("header_name").asText(""),
                    n.path("header_value").asText(""));
        } catch (Exception e) {
            throw new HttpException("decode", "/api/v1/attest", 0, body);
        }
    }
}
