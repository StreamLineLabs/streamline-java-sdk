package io.streamline.attestation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttestationVerifierTest {

    private static class StubRecord implements AttestationVerifier.AttestedRecord {
        final Map<String, byte[]> headers = new HashMap<>();
        public String topic() { return "orders"; }
        public int partition() { return 0; }
        public long offset() { return 42L; }
        public byte[] value() { return "{\"amount\":100}".getBytes(StandardCharsets.UTF_8); }
        public long timestampMs() { return 1_700_000_000_000L; }
        public int schemaId() { return 7; }
        public String keyId() { return "broker-key-1"; }
        public Map<String, byte[]> headers() { return headers; }
    }

    @Test
    void verifies_valid_signature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        StubRecord r = new StubRecord();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(AttestationVerifier.canonicalBytes(r));
        byte[] sig = signer.sign();

        r.headers.put(AttestationVerifier.HEADER, sig);
        assertTrue(new AttestationVerifier(kp.getPublic()).verify(r));
    }

    @Test
    void rejects_missing_header() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        assertFalse(new AttestationVerifier(kp.getPublic()).verify(new StubRecord()));
    }

    @Test
    void rejects_tampered_signature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        StubRecord r = new StubRecord();
        r.headers.put(AttestationVerifier.HEADER, new byte[]{0x00, 0x01, 0x02});
        assertFalse(new AttestationVerifier(kp.getPublic()).verify(r));
    }
}
