package com.streamline.examples;

import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;

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
 * mvn compile exec:java -pl examples -Dexec.mainClass="com.streamline.examples.SecurityUsage"
 * }</pre>
 */
public class SecurityUsage {

    public static void main(String[] args) throws Exception {
        String servers = System.getenv().getOrDefault("STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092");

        // =====================================================================
        // Example 1: TLS only (server certificate validation)
        // =====================================================================
        System.out.println("=== TLS Connection ===");
        {
            StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(servers)
                .securityProtocol("SSL")
                .sslTruststoreLocation("/path/to/ca-cert.pem")
                // For mutual TLS (mTLS), also set:
                // .sslKeystoreLocation("/path/to/client-cert.pem")
                // .sslKeystorePassword("changeit")
                .build();

            try (var client = new dev.streamline.client.Streamline(config)) {
                Producer<String, String> producer = client.createProducer(ProducerConfig.defaults());
                producer.send("secure-topic", "key", "Hello over TLS!").get();
                System.out.println("Message sent over TLS");
                producer.close();
            } catch (Exception e) {
                System.out.println("TLS example: " + e.getMessage());
                System.out.println("(Expected if server is not configured for TLS)");
            }
        }

        // =====================================================================
        // Example 2: SASL PLAIN authentication
        // =====================================================================
        System.out.println("\n=== SASL PLAIN Authentication ===");
        {
            StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(servers)
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("PLAIN")
                .saslUsername("my-user")
                .saslPassword("my-password")
                .build();

            try (var client = new dev.streamline.client.Streamline(config)) {
                Producer<String, String> producer = client.createProducer(ProducerConfig.defaults());
                producer.send("auth-topic", "key", "Hello with SASL PLAIN!").get();
                System.out.println("Message sent with SASL PLAIN");
                producer.close();
            } catch (Exception e) {
                System.out.println("SASL PLAIN example: " + e.getMessage());
                System.out.println("(Expected if server is not configured for SASL)");
            }
        }

        // =====================================================================
        // Example 3: SASL SCRAM-SHA-256 with TLS (most secure)
        // =====================================================================
        System.out.println("\n=== SASL SCRAM-SHA-256 + TLS ===");
        {
            StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(servers)
                .securityProtocol("SASL_SSL")
                .saslMechanism("SCRAM-SHA-256")
                .saslUsername("my-user")
                .saslPassword("my-password")
                .sslTruststoreLocation("/path/to/ca-cert.pem")
                .build();

            try (var client = new dev.streamline.client.Streamline(config)) {
                Producer<String, String> producer = client.createProducer(ProducerConfig.defaults());
                producer.send("secure-auth-topic", "key", "Hello with SCRAM + TLS!").get();
                System.out.println("Message sent with SCRAM-SHA-256 + TLS");
                producer.close();
            } catch (Exception e) {
                System.out.println("SCRAM + TLS example: " + e.getMessage());
                System.out.println("(Expected if server is not configured for SASL+TLS)");
            }
        }

        System.out.println("\nDone! Adjust paths and credentials for your environment.");
    }
}
