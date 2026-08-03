package dev.streamline.examples;

import dev.streamline.client.Streamline;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.security.SaslConfig;
import dev.streamline.client.security.SaslMechanism;
import dev.streamline.client.security.SecurityProtocol;
import dev.streamline.client.security.TlsConfig;

/**
 * Demonstrates TLS and SASL authentication configuration.
 *
 * <p>This example shows how to connect to a Streamline server with:
 * <ul>
 *   <li>TLS encryption (SSL/mTLS)</li>
 *   <li>SASL authentication (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512)</li>
 * </ul>
 *
 * <pre>{@code
 * # Start Streamline with TLS + SASL
 * streamline --tls-cert server.pem --tls-key server-key.pem --sasl-enabled
 *
 * # Run this example
 * mvn compile exec:java -pl examples -Dexec.mainClass="dev.streamline.examples.SecurityUsage"
 * }</pre>
 */
public class SecurityUsage {

    public static void main(String[] args) {
        String servers = ExampleEnv.bootstrapServers();

        // =====================================================================
        // Example 1: TLS only (server certificate validation)
        // =====================================================================
        System.out.println("=== TLS Connection ===");
        send("secure-topic", "Hello over TLS!", Streamline.builder()
                .bootstrapServers(servers)
                .securityProtocol(SecurityProtocol.SSL)
                .tlsConfig(TlsConfig.builder()
                        .truststoreLocation("/path/to/truststore.jks")
                        .truststorePassword("changeit")
                        // For mutual TLS (mTLS), also set:
                        // .keystoreLocation("/path/to/keystore.jks")
                        // .keystorePassword("changeit")
                        .build()));

        // =====================================================================
        // Example 2: SASL PLAIN authentication
        // =====================================================================
        System.out.println("\n=== SASL PLAIN Authentication ===");
        send("auth-topic", "Hello with SASL PLAIN!", Streamline.builder()
                .bootstrapServers(servers)
                .securityProtocol(SecurityProtocol.SASL_PLAINTEXT)
                .saslConfig(SaslConfig.builder()
                        .mechanism(SaslMechanism.PLAIN)
                        .username("my-user")
                        .password("my-password")
                        .build()));

        // =====================================================================
        // Example 3: SASL SCRAM-SHA-256 with TLS (most secure)
        // =====================================================================
        System.out.println("\n=== SASL SCRAM-SHA-256 + TLS ===");
        send("secure-auth-topic", "Hello with SCRAM + TLS!", Streamline.builder()
                .bootstrapServers(servers)
                .securityProtocol(SecurityProtocol.SASL_SSL)
                .saslConfig(SaslConfig.builder()
                        .mechanism(SaslMechanism.SCRAM_SHA_256)
                        .username("my-user")
                        .password("my-password")
                        .build())
                .tlsConfig(TlsConfig.builder()
                        .truststoreLocation("/path/to/truststore.jks")
                        .truststorePassword("changeit")
                        .build()));

        System.out.println("\nDone! Adjust paths and credentials for your environment.");
    }

    private static void send(String topic, String message, Streamline.Builder builder) {
        try (Streamline client = builder.build();
             Producer<String, String> producer = client.createProducer(ProducerConfig.defaults())) {
            producer.send(topic, "key", message).join();
            System.out.println("Message sent to " + topic);
        } catch (RuntimeException e) {
            System.out.println("Skipped: " + e.getMessage());
            System.out.println("(Expected if the server is not configured for this protocol)");
        }
    }
}
