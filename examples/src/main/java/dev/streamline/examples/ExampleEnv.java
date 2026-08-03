package dev.streamline.examples;

/**
 * Endpoints shared by the examples, overridable through the environment so the same
 * code runs against a playground, a container, or a remote cluster.
 */
final class ExampleEnv {

    private ExampleEnv() {
    }

    /** Kafka-protocol endpoint; {@code STREAMLINE_BOOTSTRAP_SERVERS}. */
    static String bootstrapServers() {
        return env("STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092");
    }

    /** HTTP API endpoint; {@code STREAMLINE_HTTP_URL}. */
    static String httpUrl() {
        return env("STREAMLINE_HTTP_URL", "http://localhost:9094");
    }

    /** Schema Registry endpoint; {@code STREAMLINE_SCHEMA_REGISTRY_URL}, defaults to the HTTP API. */
    static String schemaRegistryUrl() {
        return env("STREAMLINE_SCHEMA_REGISTRY_URL", httpUrl());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
