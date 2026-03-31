package dev.streamline.client;

import dev.streamline.client.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies {@code streamline-attest} headers on consumed records using a local
 * Ed25519 public key. No network calls are made.
 *
 * <p>The attestation header contains a Base64-encoded JSON envelope with an
 * Ed25519 signature over the canonical bytes:
 * {@code topic|partition|offset|payload_sha256|schema_id|timestamp_ms|key_id}.
 *
 * <p>Example usage:
 * <pre>{@code
 * KeyFactory kf = KeyFactory.getInstance("Ed25519");
 * PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(derBytes));
 * StreamlineVerifier verifier = new StreamlineVerifier(pubKey);
 *
 * VerificationResult result = verifier.verify(consumerRecord);
 * if (result.verified()) {
 *     System.out.println("Verified from " + result.producerId());
 * }
 * }</pre>
 *
 * <p>Stability tier: <b>Experimental</b>.
 */
public final class StreamlineVerifier {

    /** Kafka header name carrying the attestation envelope. */
    public static final String ATTEST_HEADER = "streamline-attest";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PublicKey publicKey;

    /**
     * Creates a verifier backed by the given Ed25519 public key.
     *
     * @param publicKey an Ed25519 public key
     */
    public StreamlineVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Verify the attestation on a consumer record.
     *
     * @param record the consumed record to verify
     * @return a {@link VerificationResult} indicating success or failure
     */
    public VerificationResult verify(ConsumerRecord<?, ?> record) {
        String headerValue = record.headers().get(ATTEST_HEADER);
        if (headerValue == null) {
            return VerificationResult.failed();
        }

        JsonNode attestation;
        try {
            byte[] decoded = Base64.getDecoder().decode(headerValue);
            attestation = MAPPER.readTree(decoded);
        } catch (Exception e) {
            return VerificationResult.failed();
        }

        String payloadSha256;
        String topic;
        int partition;
        long offset;
        int schemaId;
        long timestampMs;
        String keyId;
        String signatureB64;
        try {
            payloadSha256 = attestation.get("payload_sha256").asText();
            topic = attestation.get("topic").asText();
            partition = attestation.get("partition").asInt();
            offset = attestation.get("offset").asLong();
            schemaId = attestation.get("schema_id").asInt();
            timestampMs = attestation.get("timestamp_ms").asLong();
            keyId = attestation.get("key_id").asText();
            signatureB64 = attestation.get("signature").asText();
        } catch (NullPointerException e) {
            return VerificationResult.failed();
        }

        String canonical = topic + "|" + partition + "|" + offset
                + "|" + payloadSha256
                + "|" + schemaId
                + "|" + timestampMs
                + "|" + keyId;
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);

        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureB64);
        } catch (IllegalArgumentException e) {
            return VerificationResult.failed();
        }

        boolean verified;
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(canonicalBytes);
            verified = sig.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            verified = false;
        }

        String contractId = attestation.has("contract_id")
                ? attestation.get("contract_id").asText()
                : null;

        return new VerificationResult(
                verified,
                keyId,
                schemaId != 0 ? schemaId : null,
                contractId,
                timestampMs
        );
    }

    /**
     * The result of an attestation verification.
     *
     * @param verified    whether the signature was valid
     * @param producerId  the key id from the attestation
     * @param schemaId    the schema id (null if zero / absent)
     * @param contractId  optional contract id
     * @param timestampMs attestation timestamp in epoch milliseconds
     */
    public record VerificationResult(
            boolean verified,
            String producerId,
            Integer schemaId,
            String contractId,
            long timestampMs
    ) {
        static VerificationResult failed() {
            return new VerificationResult(false, "", null, null, 0);
        }
    }
}
