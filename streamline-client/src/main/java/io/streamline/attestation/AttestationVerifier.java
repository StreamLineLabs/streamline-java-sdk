package io.streamline.attestation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.InvalidKeyException;
import java.util.Map;

/**
 * Verifies <code>streamline-attest</code> headers attached by Streamline
 * brokers when contract attestation is enabled (M4).
 *
 * <p>Stability tier: <b>Experimental</b>. API may change before M4 GA.
 */
public final class AttestationVerifier {

    public static final String HEADER = "streamline-attest";

    private final PublicKey publicKey;

    public AttestationVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Verify the attestation header on a record. Returns true iff the
     * signature is valid for the canonical bytes.
     */
    public boolean verify(AttestedRecord record) {
        byte[] sig = record.headers().get(HEADER);
        if (sig == null) {
            return false;
        }
        byte[] canonical = canonicalBytes(record);
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(publicKey);
            s.update(canonical);
            return s.verify(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    static byte[] canonicalBytes(AttestedRecord r) {
        String s = r.topic() + "|" + r.partition() + "|" + r.offset()
                + "|" + sha256Hex(r.value())
                + "|" + r.schemaId()
                + "|" + r.timestampMs()
                + "|" + r.keyId();
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String sha256Hex(byte[] value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(value);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Minimal record interface needed for verification. */
    public interface AttestedRecord {
        String topic();
        int partition();
        long offset();
        byte[] value();
        long timestampMs();
        int schemaId();
        String keyId();
        Map<String, byte[]> headers();
    }
}
