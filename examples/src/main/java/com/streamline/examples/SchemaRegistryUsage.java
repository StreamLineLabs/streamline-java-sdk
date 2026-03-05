package com.streamline.examples;

import com.streamline.client.StreamlineClient;
import com.streamline.client.StreamlineConfig;
import com.streamline.client.consumer.ConsumerConfig;
import com.streamline.client.consumer.ConsumerRecord;
import com.streamline.client.producer.ProducerRecord;
import com.streamline.client.schema.SchemaRegistryClient;
import com.streamline.client.schema.SchemaType;

import java.time.Duration;
import java.util.List;

/**
 * Schema Registry example demonstrating Avro schema management and
 * validated produce/consume with the Streamline Java SDK.
 *
 * <p>Ensure a Streamline server is running at localhost:9092 with the
 * schema registry enabled on port 9094 before running.
 *
 * <pre>{@code
 * # Start Streamline
 * streamline --playground
 *
 * # Run this example
 * mvn compile exec:java -pl examples \
 *   -Dexec.mainClass="com.streamline.examples.SchemaRegistryUsage"
 * }</pre>
 */
public class SchemaRegistryUsage {

    // Avro schema for a User record
    private static final String USER_SCHEMA = """
            {
              "type": "record",
              "name": "User",
              "namespace": "com.streamline.examples",
              "fields": [
                {"name": "id",         "type": "int"},
                {"name": "name",       "type": "string"},
                {"name": "email",      "type": "string"},
                {"name": "created_at", "type": "string"}
              ]
            }
            """;

    private static final String SUBJECT = "users-value";
    private static final String TOPIC = "users";

    public static void main(String[] args) throws Exception {
        // === 1. Configure the Streamline client ===
        StreamlineConfig config = StreamlineConfig.builder()
                .bootstrapServers(System.getenv().getOrDefault(
                        "STREAMLINE_BOOTSTRAP_SERVERS", "localhost:9092"))
                .clientId("java-schema-example")
                .build();

        // === 2. Create a schema registry client ===
        SchemaRegistryClient registry = new SchemaRegistryClient(
                System.getenv().getOrDefault(
                        "STREAMLINE_SCHEMA_REGISTRY_URL", "http://localhost:9094"));

        try (StreamlineClient client = new StreamlineClient(config)) {
            // === 3. Register an Avro schema ===
            System.out.println("=== Registering Schema ===");
            int schemaId = registry.register(SUBJECT, USER_SCHEMA, SchemaType.AVRO);
            System.out.printf("Registered schema with id=%d for subject=%s%n",
                    schemaId, SUBJECT);

            // Retrieve the schema back by id
            String retrieved = registry.getSchema(schemaId);
            System.out.printf("Retrieved schema: %s%n",
                    retrieved.replaceAll("\\s+", " ").trim());

            // === 4. Check schema compatibility ===
            System.out.println("\n=== Checking Compatibility ===");
            boolean compatible = registry.checkCompatibility(
                    SUBJECT, USER_SCHEMA, SchemaType.AVRO);
            System.out.printf("Schema compatible: %s%n", compatible);

            // === 5. Produce messages with schema validation ===
            System.out.println("\n=== Producing Messages with Schema ===");
            for (int i = 0; i < 5; i++) {
                String userJson = String.format(
                        "{\"id\":%d,\"name\":\"user-%d\",\"email\":\"user%d@example.com\","
                                + "\"created_at\":\"2025-01-15T10:00:00Z\"}",
                        i, i, i);

                ProducerRecord<String, String> record = ProducerRecord.<String, String>builder()
                        .topic(TOPIC)
                        .key("user-" + i)
                        .value(userJson)
                        .schemaId(schemaId)
                        .build();

                client.produce(record);
                System.out.printf("Produced user-%d with schema id=%d%n", i, schemaId);
            }

            // === 6. Consume and deserialize with schema ===
            System.out.println("\n=== Consuming Messages with Schema ===");
            ConsumerConfig consumerConfig = ConsumerConfig.builder()
                    .groupId("java-schema-example-group")
                    .autoOffsetReset("earliest")
                    .schemaRegistryUrl(registry.getUrl())
                    .build();

            List<ConsumerRecord<String, String>> records = client.poll(
                    TOPIC, consumerConfig, Duration.ofSeconds(5));

            for (ConsumerRecord<String, String> record : records) {
                System.out.printf(
                        "Received: partition=%d, offset=%d, key=%s, value=%s%n",
                        record.partition(), record.offset(),
                        record.key(), record.value());
            }

            System.out.println("\nDone!");
        }
    }
}
