package dev.streamline.client.security;

/**
 * SASL authentication mechanism.
 */
public enum SaslMechanism {
    PLAIN,
    SCRAM_SHA_256,
    SCRAM_SHA_512
}
