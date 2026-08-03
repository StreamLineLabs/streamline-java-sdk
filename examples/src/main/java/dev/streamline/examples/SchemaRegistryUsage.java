package dev.streamline.examples;

import dev.streamline.client.Streamline;
import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.consumer.ConsumerRecord;
import dev.streamline.client.producer.Producer;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.client.schema.CompatibilityLevel;
import dev.streamline.client.schema.SchemaRegistryClient;
import dev.streamline.client.schema.SchemaType;

import java.time.Duration;
import java.util.List;

/**
 * Schema Registry example demonstrating Avro schema management and
 * produce/consume with the Streamline Java SDK.
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
 *   -Dexec.mainClass="dev.streamline.examples.SchemaRegistryUsage"
 * }</pre>
 */
public class SchemaRegistryUsage {

    // Avro schema for a User record
    private static final String USER_SCHEMA = """
            {
              "type": "record",
              "name": "User",
              "namespace": "dev.streamline.examples",
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
        try (SchemaRegistryClient registry = new SchemaRegistryClient(ExampleEnv.schemaRegistryUrl());
             Streamline client = Streamline.builder()
                     .bootstrapServers(ExampleEnv.bootstrapServers())
                     .httpEndpoint(ExampleEnv.httpUrl())
                     .build()) {

            // === 1. Register an Avro schema ===
            System.out.println("=== Registering Schema ===");
            int schemaId = registry.register(SUBJECT, USER_SCHEMA, SchemaType.AVRO);
            System.out.printf("Registered schema with id=%d for subject=%s%n", schemaId, SUBJECT);

            String retrieved = registry.getSchema(schemaId);
            System.out.printf("Retrieved schema: %s%n", retrieved.replaceAll("\\s+", " ").trim());

            // === 2. Check schema compatibility ===
            System.out.println("\n=== Checking Compatibility ===");
            boolean compatible = registry.checkCompatibility(SUBJECT, USER_SCHEMA, SchemaType.AVRO);
            System.out.printf("Schema compatible: %s%n", compatible);

            CompatibilityLevel level = registry.getCompatibilityLevel(SUBJECT);
            System.out.printf("Compatibility level: %s%n", level);

            // === 3. Produce messages validated against the schema ===
            System.out.println("\n=== Producing Messages ===");
            try (Producer<String, String> producer = client.createProducer(ProducerConfig.defaults())) {
                for (int i = 0; i < 5; i++) {
                    String userJson = String.format(
                            "{\"id\":%d,\"name\":\"user-%d\",\"email\":\"user%d@example.com\","
                                    + "\"created_at\":\"2025-01-15T10:00:00Z\"}",
                            i, i, i);

                    producer.send(TOPIC, "user-" + i, userJson).join();
                    System.out.printf("Produced user-%d%n", i);
                }
            }

            // === 4. Consume the messages back ===
            System.out.println("\n=== Consuming Messages ===");
            ConsumerConfig consumerConfig = ConsumerConfig.builder()
                    .groupId("java-schema-example-group")
                    .autoOffsetReset("earliest")
                    .build();

            try (Consumer<String, String> consumer = client.consumer(TOPIC, consumerConfig)) {
                consumer.subscribe();

                List<ConsumerRecord<String, String>> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("Received: partition=%d, offset=%d, key=%s, value=%s%n",
                            record.partition(), record.offset(), record.key(), record.value());
                }
            }

            System.out.println("\n=== Subjects ===");
            System.out.println(registry.listSubjects());

            System.out.println("\nDone!");
        }
    }
}
