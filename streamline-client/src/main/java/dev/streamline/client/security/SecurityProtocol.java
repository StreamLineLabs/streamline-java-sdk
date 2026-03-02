package dev.streamline.client.security;

/**
 * Security protocol for client-broker communication.
 */
public enum SecurityProtocol {
    PLAINTEXT,
    SSL,
    SASL_PLAINTEXT,
    SASL_SSL
}
