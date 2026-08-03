package io.streamline.testcontainers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StreamlineContainer.
 *
 * <p>These need a working Docker daemon and a pullable Streamline image, so they only run
 * when explicitly enabled:
 * <pre>{@code
 * STREAMLINE_INTEGRATION=1 mvn verify -Pintegration
 * }</pre>
 *
 * <p>{@code STREAMLINE_IMAGE} overrides the image (for example a locally built one); it
 * defaults to {@code ghcr.io/streamlinelabs/streamline:latest}.
 *
 * <p>The gate is an {@code ExecutionCondition} rather than an assumption so that the
 * Testcontainers extension never starts — and never pulls — an image when disabled.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = StreamlineContainerIT.ENABLED_VAR, matches = "1",
        disabledReason = "set STREAMLINE_INTEGRATION=1 and run with -Pintegration")
@Testcontainers
class StreamlineContainerIT {

    /** Opt-in switch; without it the suite is skipped rather than pulling images. */
    static final String ENABLED_VAR = "STREAMLINE_INTEGRATION";

    /** Overrides the image under test, e.g. {@code ghcr.io/streamlinelabs/streamline:0.3.0}. */
    static final String IMAGE_VAR = "STREAMLINE_IMAGE";

    @Container
    static StreamlineContainer streamline = new StreamlineContainer(image())
            .withDebugLogging();

    static DockerImageName image() {
        String override = System.getenv(IMAGE_VAR);
        if (override == null || override.trim().isEmpty()) {
            return DockerImageName.parse(StreamlineContainer.DEFAULT_IMAGE)
                    .withTag(StreamlineContainer.DEFAULT_TAG);
        }
        return DockerImageName.parse(override.trim());
    }

    @Test
    void shouldStartAndProvideBootstrapServers() {
        assertTrue(streamline.isRunning());
        assertNotNull(streamline.getBootstrapServers());
        assertTrue(streamline.getBootstrapServers().contains(":"));
    }

    @Test
    void shouldProvideHttpEndpoints() {
        assertNotNull(streamline.getHttpUrl());
        assertNotNull(streamline.getHealthUrl());
        assertNotNull(streamline.getMetricsUrl());
    }

    @Test
    void shouldRespondToHealthCheck() throws Exception {
        URL url = new URL(streamline.getHealthUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);

        conn.disconnect();
    }

    @Test
    void shouldProduceAndConsumeMessages() throws Exception {
        String topic = "test-topic-" + UUID.randomUUID();
        String key = "test-key";
        String value = "test-value-" + UUID.randomUUID();

        // Create topic using Admin client
        try (AdminClient admin = createAdminClient()) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                 .all()
                 .get();
        }

        // Produce a message
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }

        // Consume the message
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertFalse(records.isEmpty(), "Should have received at least one record");
            assertEquals(1, records.count());
            assertEquals(key, records.iterator().next().key());
            assertEquals(value, records.iterator().next().value());
        }
    }

    @Test
    void shouldCreateTopicWithCli() throws Exception {
        String topic = "cli-created-topic-" + UUID.randomUUID();

        // Create topic using container's CLI
        streamline.createTopic(topic, 3);

        // Verify topic exists by producing to it
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, "key", "value")).get();
        }
    }

    private AdminClient createAdminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, streamline.getBootstrapServers());
        return AdminClient.create(props);
    }

    private KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, streamline.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, streamline.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }
}
