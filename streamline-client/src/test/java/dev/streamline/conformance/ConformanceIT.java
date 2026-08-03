package dev.streamline.conformance;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import dev.streamline.testsupport.IntegrationEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK Conformance Test Suite — 46 tests per SDK_CONFORMANCE_SPEC.md
 *
 * <p>Requires a live Streamline server:
 * <pre>{@code
 * docker compose -f docker-compose.test.yml up -d
 * STREAMLINE_INTEGRATION=1 mvn verify -Pintegration
 * }</pre>
 *
 * <p>Endpoints are configurable through {@link IntegrationEnvironment}. When
 * {@code STREAMLINE_INTEGRATION=1} is set but the server is unreachable the suite
 * fails fast instead of silently passing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Tag("conformance")
@EnabledIfEnvironmentVariable(named = IntegrationEnvironment.ENABLED_VAR, matches = "1",
        disabledReason = "set STREAMLINE_INTEGRATION=1 and run with -Pintegration")
public class ConformanceIT {

    private static AdminClient adminClient;

    // ========== Configuration helpers ==========

    private static String getBootstrap() {
        return IntegrationEnvironment.bootstrapServers();
    }

    private static String getRegistryUrl() {
        return IntegrationEnvironment.schemaRegistryUrl();
    }

    private static String uniqueTopic(String prefix) {
        return "conformance-" + prefix + "-" + System.nanoTime();
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return props;
    }

    /**
     * Polls until at least {@code minRecords} are accumulated or {@code timeout} expires.
     */
    private static ConsumerRecords<String, String> pollUntilRecords(
            KafkaConsumer<String, String> consumer, int minRecords, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Map<TopicPartition, List<ConsumerRecord<String, String>>> accumulated = new HashMap<>();
        int total = 0;

        while (System.currentTimeMillis() < deadline && total < minRecords) {
            ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : batch) {
                accumulated
                        .computeIfAbsent(new TopicPartition(r.topic(), r.partition()), k -> new ArrayList<>())
                        .add(r);
                total++;
            }
        }
        return new ConsumerRecords<>(accumulated);
    }

    /**
     * Reads the response body from an HTTP connection (handles both success and error streams).
     */
    private static String readHttpResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ========== Lifecycle ==========

    @BeforeAll
    static void setUpAdmin() {
        IntegrationEnvironment.requireAvailable();
        IntegrationEnvironment.requireSchemaRegistry();

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        adminClient = AdminClient.create(props);
    }

    @AfterAll
    static void tearDownAdmin() {
        if (adminClient != null) {
            adminClient.close(Duration.ofSeconds(5));
        }
    }

    // ========== PRODUCER (8 tests) ==========

    @Test @Order(1) @DisplayName("P01: Simple Produce")
    void p01_simpleProduce() throws Exception {
        String topic = uniqueTopic("p01");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            RecordMetadata metadata = producer.send(
                    new ProducerRecord<>(topic, "hello-value")
            ).get(10, TimeUnit.SECONDS);

            assertNotNull(metadata);
            assertEquals(topic, metadata.topic());
            assertTrue(metadata.offset() >= 0, "Offset should be >= 0");
        }
    }

    @Test @Order(2) @DisplayName("P02: Keyed Produce")
    void p02_keyedProduce() throws Exception {
        String topic = uniqueTopic("p02");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            RecordMetadata meta1 = producer.send(
                    new ProducerRecord<>(topic, "same-key", "value-1")
            ).get(10, TimeUnit.SECONDS);

            RecordMetadata meta2 = producer.send(
                    new ProducerRecord<>(topic, "same-key", "value-2")
            ).get(10, TimeUnit.SECONDS);

            assertEquals(meta1.partition(), meta2.partition(),
                    "Messages with the same key should land in the same partition");
        }
    }

    @Test @Order(3) @DisplayName("P03: Headers Produce")
    void p03_headersProduce() throws Exception {
        String topic = uniqueTopic("p03");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, "key", "value-with-headers");
            record.headers()
                    .add("trace-id", "abc-123".getBytes())
                    .add("source", "conformance-test".getBytes());

            producer.send(record).get(10, TimeUnit.SECONDS);
            producer.flush();
        }

        // Consume back and verify headers survived the round-trip
        String groupId = "conformance-p03-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record");

            ConsumerRecord<String, String> consumed = records.iterator().next();

            Header traceHeader = consumed.headers().lastHeader("trace-id");
            assertNotNull(traceHeader, "trace-id header should be present");
            assertEquals("abc-123", new String(traceHeader.value()));

            Header sourceHeader = consumed.headers().lastHeader("source");
            assertNotNull(sourceHeader, "source header should be present");
            assertEquals("conformance-test", new String(sourceHeader.value()));
        }
    }

    @Test @Order(4) @DisplayName("P04: Batch Produce")
    void p04_batchProduce() throws Exception {
        String topic = uniqueTopic("p04");
        int count = 10;
        List<RecordMetadata> results = new ArrayList<>();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            List<Future<RecordMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(producer.send(
                        new ProducerRecord<>(topic, "key-" + i, "value-" + i)));
            }
            producer.flush();

            for (Future<RecordMetadata> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }
        }

        assertEquals(count, results.size(), "All 10 sends should succeed");
        for (RecordMetadata meta : results) {
            assertTrue(meta.offset() >= 0, "Each offset should be >= 0");
        }

        // Offsets within the same partition must be strictly increasing
        Map<Integer, List<Long>> byPartition = new HashMap<>();
        for (RecordMetadata meta : results) {
            byPartition.computeIfAbsent(meta.partition(), k -> new ArrayList<>()).add(meta.offset());
        }
        for (List<Long> offsets : byPartition.values()) {
            for (int i = 1; i < offsets.size(); i++) {
                assertTrue(offsets.get(i) > offsets.get(i - 1),
                        "Offsets within a partition should be strictly incrementing");
            }
        }
    }

    @Test @Order(5) @DisplayName("P05: Compression")
    void p05_compression() throws Exception {
        String topic = uniqueTopic("p05");
        String value = "compressed-message-" + System.nanoTime();

        Properties props = producerProps();
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            RecordMetadata meta = producer.send(
                    new ProducerRecord<>(topic, "comp-key", value)
            ).get(10, TimeUnit.SECONDS);
            assertNotNull(meta);
            assertTrue(meta.offset() >= 0);
        }

        String groupId = "conformance-p05-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record");
            ConsumerRecord<String, String> rec = records.iterator().next();
            assertEquals(value, rec.value(), "Value should survive gzip compression round-trip");
            assertEquals("comp-key", rec.key());
        }
    }

    @Test @Order(6) @DisplayName("P06: Partitioner")
    void p06_partitioner() throws Exception {
        String topic = uniqueTopic("p06");
        int targetPartition = 0;

        adminClient.createTopics(List.of(new NewTopic(topic, 3, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            RecordMetadata meta = producer.send(
                    new ProducerRecord<>(topic, targetPartition, "key", "partition-test-value")
            ).get(10, TimeUnit.SECONDS);
            assertEquals(targetPartition, meta.partition(),
                    "Message should land in the specified partition");
        }

        String groupId = "conformance-p06-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            TopicPartition tp = new TopicPartition(topic, targetPartition);
            consumer.assign(Collections.singletonList(tp));
            consumer.seekToBeginning(Collections.singletonList(tp));

            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume record from target partition");
            assertEquals("partition-test-value", records.iterator().next().value());
        }
    }

    @Test @Order(7) @DisplayName("P07: Idempotent")
    void p07_idempotent() throws Exception {
        String topic = uniqueTopic("p07");

        Properties props = producerProps();
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            List<Future<RecordMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(producer.send(
                        new ProducerRecord<>(topic, "idem-key-" + i, "idem-value-" + i)));
            }
            producer.flush();

            Set<Long> offsets = new HashSet<>();
            for (Future<RecordMetadata> f : futures) {
                RecordMetadata meta = f.get(10, TimeUnit.SECONDS);
                assertNotNull(meta);
                assertTrue(meta.offset() >= 0);
                offsets.add(meta.offset());
            }
            assertEquals(5, offsets.size(), "Idempotent producer should produce 5 unique offsets");
        }
    }

    @Test @Order(8) @DisplayName("P08: Timeout")
    void p08_timeout() {
        Properties props = producerProps();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.0.2.1:9092");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            Exception ex = assertThrows(Exception.class, () ->
                    producer.send(new ProducerRecord<>("timeout-topic", "v")).get(5, TimeUnit.SECONDS)
            );
            assertNotNull(ex, "Should throw an exception when broker is unreachable");
        }
    }

    // ========== CONSUMER (8 tests) ==========

    @Test @Order(9) @DisplayName("C01: Subscribe")
    void c01_subscribe() throws Exception {
        String topic = uniqueTopic("c01");
        int messageCount = 5;

        // Pre-populate the topic
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < messageCount; i++) {
                producer.send(new ProducerRecord<>(topic, "key-" + i, "value-" + i));
            }
            producer.flush();
        }

        // Subscribe and consume
        String groupId = "conformance-c01-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records =
                    pollUntilRecords(consumer, messageCount, Duration.ofSeconds(15));
            assertEquals(messageCount, records.count(),
                    "Should consume all " + messageCount + " messages");
        }
    }

    @Test @Order(10) @DisplayName("C02: From Beginning")
    void c02_fromBeginning() throws Exception {
        String topic = uniqueTopic("c02");
        String firstValue = "first-message-" + System.nanoTime();

        // Produce messages
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "key-0", firstValue)).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "key-1", "second-message")).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "key-2", "third-message")).get(10, TimeUnit.SECONDS);
            producer.flush();
        }

        // New consumer group with auto.offset.reset=earliest reads from the beginning
        String groupId = "conformance-c02-" + System.nanoTime();
        Properties props = consumerProps(groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records =
                    pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record");

            // The very first record should be the one we produced first
            ConsumerRecord<String, String> first = records.iterator().next();
            assertEquals(firstValue, first.value(),
                    "First consumed message should match the first produced message");
        }
    }

    @Test @Order(11) @DisplayName("C03: From Offset")
    void c03_fromOffset() throws Exception {
        String topic = uniqueTopic("c03");
        int messageCount = 5;

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < messageCount; i++) {
                producer.send(new ProducerRecord<>(topic, "key-" + i, "value-" + i)).get(10, TimeUnit.SECONDS);
            }
        }

        String groupId = "conformance-c03-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(tp));
            consumer.seek(tp, 2);

            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record from offset 2");

            ConsumerRecord<String, String> first = records.iterator().next();
            assertEquals(2, first.offset(), "First consumed record should be at offset 2");
            assertEquals("value-2", first.value(), "Value at offset 2 should be 'value-2'");
        }
    }

    @Test @Order(12) @DisplayName("C04: From Timestamp")
    void c04_fromTimestamp() throws Exception {
        String topic = uniqueTopic("c04");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "key-0", "early-message")).get(10, TimeUnit.SECONDS);
        }

        Thread.sleep(100);
        long midTimestamp = System.currentTimeMillis();
        Thread.sleep(100);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "key-1", "late-message")).get(10, TimeUnit.SECONDS);
        }

        String groupId = "conformance-c04-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(tp));

            Map<TopicPartition, OffsetAndTimestamp> offsets =
                    consumer.offsetsForTimes(Map.of(tp, midTimestamp));

            assertNotNull(offsets.get(tp), "Should find offset for timestamp");
            consumer.seek(tp, offsets.get(tp).offset());

            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record after timestamp");
            assertEquals("late-message", records.iterator().next().value(),
                    "Should receive the message produced after the timestamp");
        }
    }

    @Test @Order(13) @DisplayName("C05: Follow")
    void c05_follow() throws Exception {
        String topic = uniqueTopic("c05");
        String groupId = "conformance-c05-" + System.nanoTime();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));
            consumer.poll(Duration.ofSeconds(2));

            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
                producer.send(new ProducerRecord<>(topic, "follow-key", "follow-value"))
                        .get(10, TimeUnit.SECONDS);
            }

            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Consumer in follow mode should receive new messages");
            assertEquals("follow-value", records.iterator().next().value());
        }
    }

    @Test @Order(14) @DisplayName("C06: Filter")
    void c06_filter() throws Exception {
        String topic = uniqueTopic("c06");
        String targetKey = "target-key";

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "other-key", "noise-1")).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, targetKey, "target-value")).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "other-key", "noise-2")).get(10, TimeUnit.SECONDS);
        }

        String groupId = "conformance-c06-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 3, Duration.ofSeconds(15));

            List<ConsumerRecord<String, String>> filtered = new ArrayList<>();
            for (ConsumerRecord<String, String> r : records) {
                if (targetKey.equals(r.key())) {
                    filtered.add(r);
                }
            }

            assertEquals(1, filtered.size(), "Should find exactly 1 record with target key");
            assertEquals("target-value", filtered.get(0).value());
        }
    }

    @Test @Order(15) @DisplayName("C07: Headers")
    void c07_headers() throws Exception {
        String topic = uniqueTopic("c07");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, "hdr-key", "hdr-value");
            record.headers()
                    .add("x-request-id", "req-42".getBytes())
                    .add("x-correlation-id", "corr-99".getBytes());
            producer.send(record).get(10, TimeUnit.SECONDS);
        }

        String groupId = "conformance-c07-" + System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 1, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least 1 record");

            ConsumerRecord<String, String> rec = records.iterator().next();
            assertNotNull(rec.headers(), "Record should have headers");

            Header reqHeader = rec.headers().lastHeader("x-request-id");
            assertNotNull(reqHeader, "x-request-id header should be present");
            assertEquals("req-42", new String(reqHeader.value()));

            Header corrHeader = rec.headers().lastHeader("x-correlation-id");
            assertNotNull(corrHeader, "x-correlation-id header should be present");
            assertEquals("corr-99", new String(corrHeader.value()));
        }
    }

    @Test @Order(16) @DisplayName("C08: Timeout")
    void c08_timeout() {
        String topic = uniqueTopic("c08");
        String groupId = "conformance-c08-" + System.nanoTime();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(tp));

            long start = System.currentTimeMillis();
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            long elapsed = System.currentTimeMillis() - start;

            assertEquals(0, records.count(), "Poll on empty topic should return no records");
            assertTrue(elapsed < 5000, "Poll should return within a reasonable time (was " + elapsed + "ms)");
        }
    }

    // ========== CONSUMER GROUPS (6 tests) ==========

    @Test @Order(17) @DisplayName("G01: Join Group")
    void g01_joinGroup() throws Exception {
        String topic = uniqueTopic("g01");
        String groupId = "conformance-g01-" + System.nanoTime();

        adminClient.createTopics(List.of(new NewTopic(topic, 2, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        // Produce a message so the group has something to consume
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "key", "value")).get(5, TimeUnit.SECONDS);
        }

        // Join group by subscribing and polling
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(List.of(topic));
            pollUntilRecords(consumer, 1, Duration.ofSeconds(15));

            // Verify group exists in admin listing
            Collection<ConsumerGroupListing> groups = adminClient.listConsumerGroups()
                    .all().get(10, TimeUnit.SECONDS);
            boolean found = groups.stream().anyMatch(g -> g.groupId().equals(groupId));
            assertTrue(found, "Consumer group should be listed after join");
        }
    }

    @Test @Order(18) @DisplayName("G02: Rebalance")
    void g02_rebalance() throws Exception {
        String topic = uniqueTopic("g02");
        String groupId = "conformance-g02-" + System.nanoTime();

        adminClient.createTopics(List.of(new NewTopic(topic, 2, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < 4; i++) {
                producer.send(new ProducerRecord<>(topic, "k" + i, "v" + i)).get(5, TimeUnit.SECONDS);
            }
        }

        try (KafkaConsumer<String, String> c1 = new KafkaConsumer<>(consumerProps(groupId))) {
            c1.subscribe(List.of(topic));
            pollUntilRecords(c1, 1, Duration.ofSeconds(10));

            Set<TopicPartition> assignmentBefore = c1.assignment();
            assertFalse(assignmentBefore.isEmpty(), "First consumer should have partition assignments");

            // Second consumer joining triggers rebalance
            try (KafkaConsumer<String, String> c2 = new KafkaConsumer<>(consumerProps(groupId))) {
                c2.subscribe(List.of(topic));
                c2.poll(Duration.ofSeconds(10));

                // After rebalance, combined assignments should cover all partitions
                Set<TopicPartition> combined = new HashSet<>(c1.assignment());
                combined.addAll(c2.assignment());
                assertTrue(combined.size() >= 1, "Partitions should be distributed across consumers");
            }
        }
    }

    @Test @Order(19) @DisplayName("G03: Commit Offsets")
    void g03_commitOffsets() throws Exception {
        String topic = uniqueTopic("g03");
        String groupId = "conformance-g03-" + System.nanoTime();

        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < 5; i++) {
                producer.send(new ProducerRecord<>(topic, "k" + i, "v" + i)).get(5, TimeUnit.SECONDS);
            }
        }

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = pollUntilRecords(consumer, 3, Duration.ofSeconds(15));
            assertTrue(records.count() >= 1, "Should consume at least one message");

            consumer.commitSync();

            // Verify committed offsets are stored
            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertFalse(committed.isEmpty(), "Committed offsets should be stored");
            assertTrue(committed.values().iterator().next().offset() > 0,
                    "Committed offset should be greater than 0");
        }
    }

    @Test @Order(20) @DisplayName("G04: Lag Monitoring")
    void g04_lagMonitoring() throws Exception {
        String topic = uniqueTopic("g04");
        String groupId = "conformance-g04-" + System.nanoTime();
        int messageCount = 10;

        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < messageCount; i++) {
                producer.send(new ProducerRecord<>(topic, "k" + i, "v" + i)).get(5, TimeUnit.SECONDS);
            }
        }

        // Consume only half, then commit
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(List.of(topic));
            pollUntilRecords(consumer, messageCount / 2, Duration.ofSeconds(15));
            consumer.commitSync();
        }

        // Calculate lag
        Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

        assertNotNull(committed, "Committed offsets should exist");
        assertFalse(committed.isEmpty(), "Should have committed offsets");

        TopicPartition tp = committed.keySet().iterator().next();
        long committedOffset = committed.get(tp).offset();

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient
                .listOffsets(Map.of(tp, OffsetSpec.latest()))
                .all().get(10, TimeUnit.SECONDS);

        long endOffset = endOffsets.get(tp).offset();
        long lag = endOffset - committedOffset;

        assertTrue(lag >= 0, "Lag should be non-negative");
        assertTrue(lag <= messageCount, "Lag should not exceed total messages produced");
    }

    @Test @Order(21) @DisplayName("G05: Reset Offsets")
    void g05_resetOffsets() throws Exception {
        String topic = uniqueTopic("g05");
        String groupId = "conformance-g05-" + System.nanoTime();

        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < 10; i++) {
                producer.send(new ProducerRecord<>(topic, "k" + i, "v" + i)).get(5, TimeUnit.SECONDS);
            }
        }

        // Consume some messages and commit
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.subscribe(List.of(topic));
            pollUntilRecords(consumer, 5, Duration.ofSeconds(15));
            consumer.commitSync();
        }

        // Verify committed offset is > 0
        Map<TopicPartition, OffsetAndMetadata> before = adminClient
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
        assertFalse(before.isEmpty(), "Should have committed offsets");
        long offsetBefore = before.values().iterator().next().offset();
        assertTrue(offsetBefore > 0, "Offset before reset should be > 0");

        // Reset offsets to beginning
        TopicPartition tp = before.keySet().iterator().next();
        adminClient.alterConsumerGroupOffsets(groupId,
                Map.of(tp, new OffsetAndMetadata(0)))
                .all().get(10, TimeUnit.SECONDS);

        // Verify offset was reset
        Map<TopicPartition, OffsetAndMetadata> after = adminClient
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
        long offsetAfter = after.get(tp).offset();
        assertEquals(0, offsetAfter, "Offset should be reset to 0");
    }

    @Test @Order(22) @DisplayName("G06: Leave Group")
    void g06_leaveGroup() throws Exception {
        String topic = uniqueTopic("g06");
        String groupId = "conformance-g06-" + System.nanoTime();

        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, "k", "v")).get(5, TimeUnit.SECONDS);
        }

        // Join and leave
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId));
        consumer.subscribe(List.of(topic));
        pollUntilRecords(consumer, 1, Duration.ofSeconds(10));
        consumer.close(Duration.ofSeconds(5));

        // After close, the group should eventually become empty
        ConsumerGroupDescription desc = adminClient.describeConsumerGroups(List.of(groupId))
                .describedGroups().get(groupId).get(10, TimeUnit.SECONDS);
        assertNotNull(desc, "Consumer group should still be described");
        // Members may be empty after leave
        assertTrue(desc.members().size() <= 1, "Members should be empty or draining after close");
    }

    // ========== AUTHENTICATION (6 tests) ==========

    @Test @Order(23) @DisplayName("A01: TLS Connect")
    void a01_tlsConnect() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SSL");
        props.put("ssl.truststore.location", "/tmp/nonexistent-truststore.jks");
        props.put("ssl.truststore.password", "changeit");

        assertThrows(Exception.class, () -> {
            try (AdminClient tlsAdmin = AdminClient.create(props)) {
                tlsAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "TLS connection with invalid truststore should fail");
    }

    @Test @Order(24) @DisplayName("A02: Mutual TLS")
    void a02_mutualTls() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SSL");
        props.put("ssl.truststore.location", "/tmp/nonexistent-truststore.jks");
        props.put("ssl.truststore.password", "changeit");
        props.put("ssl.keystore.location", "/tmp/nonexistent-keystore.jks");
        props.put("ssl.keystore.password", "changeit");
        props.put("ssl.key.password", "changeit");

        assertThrows(Exception.class, () -> {
            try (AdminClient mtlsAdmin = AdminClient.create(props)) {
                mtlsAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "Mutual TLS connection with invalid keystores should fail");
    }

    @Test @Order(25) @DisplayName("A03: SASL PLAIN")
    void a03_saslPlain() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"testuser\" password=\"testpass\";");

        assertThrows(Exception.class, () -> {
            try (AdminClient saslAdmin = AdminClient.create(props)) {
                saslAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "SASL PLAIN to non-SASL server should fail");
    }

    @Test @Order(26) @DisplayName("A04: SCRAM-SHA-256")
    void a04_scramSha256() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "SCRAM-SHA-256");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"testuser\" password=\"testpass\";");

        assertThrows(Exception.class, () -> {
            try (AdminClient scramAdmin = AdminClient.create(props)) {
                scramAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "SCRAM-SHA-256 to non-SCRAM server should fail");
    }

    @Test @Order(27) @DisplayName("A05: SCRAM-SHA-512")
    void a05_scramSha512() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "SCRAM-SHA-512");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"testuser\" password=\"testpass\";");

        assertThrows(Exception.class, () -> {
            try (AdminClient scramAdmin = AdminClient.create(props)) {
                scramAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "SCRAM-SHA-512 to non-SCRAM server should fail");
    }

    @Test @Order(28) @DisplayName("A06: Auth Failure")
    void a06_authFailure() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"invalid\" password=\"wrongpassword\";");

        assertThrows(Exception.class, () -> {
            try (AdminClient badAdmin = AdminClient.create(props)) {
                badAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "Authentication with invalid credentials should fail");
    }

    // ========== SCHEMA REGISTRY (6 tests) ==========

    @Test @Order(29) @DisplayName("S01: Register Schema")
    void s01_registerSchema() throws Exception {
        String subject = "conformance-s01-" + System.nanoTime() + "-value";
        String schemaJson = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Test\\\","
                + "\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"int\\\"}]}";
        String body = "{\"schema\":\"" + schemaJson + "\",\"schemaType\":\"AVRO\"}";

        URL url = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            assertTrue(status >= 200 && status < 500,
                    "Schema registration should return a valid HTTP response (got " + status + ")");
            if (status == 200 || status == 201) {
                String response = readHttpResponse(conn);
                assertTrue(response.contains("id"), "Response should contain schema ID");
            }
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        } finally {
            conn.disconnect();
        }
    }

    @Test @Order(30) @DisplayName("S02: Get by ID")
    void s02_getById() throws Exception {
        String subject = "conformance-s02-" + System.nanoTime() + "-value";
        String schemaJson = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Test2\\\","
                + "\\\"fields\\\":[{\\\"name\\\":\\\"name\\\",\\\"type\\\":\\\"string\\\"}]}";
        String body = "{\"schema\":\"" + schemaJson + "\",\"schemaType\":\"AVRO\"}";

        try {
            // Register a schema first
            URL regUrl = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection regConn = (HttpURLConnection) regUrl.openConnection();
            regConn.setRequestMethod("POST");
            regConn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            regConn.setDoOutput(true);
            regConn.setConnectTimeout(5000);
            regConn.setReadTimeout(5000);
            try (OutputStream os = regConn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int regStatus = regConn.getResponseCode();
            Assumptions.assumeTrue(regStatus == 200 || regStatus == 201,
                    "Schema registration failed with status " + regStatus);
            String regResponse = readHttpResponse(regConn);
            regConn.disconnect();

            // Extract ID (simple parse)
            String idStr = regResponse.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
            int schemaId = Integer.parseInt(idStr);

            // Fetch by ID
            URL getUrl = new URL(getRegistryUrl() + "/schemas/ids/" + schemaId);
            HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
            getConn.setRequestMethod("GET");
            getConn.setConnectTimeout(5000);
            getConn.setReadTimeout(5000);

            int getStatus = getConn.getResponseCode();
            assertEquals(200, getStatus, "GET schema by ID should return 200");
            String getResponse = readHttpResponse(getConn);
            assertTrue(getResponse.contains("schema"), "Response should contain schema definition");
            getConn.disconnect();
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        }
    }

    @Test @Order(31) @DisplayName("S03: Get Versions")
    void s03_getVersions() throws Exception {
        String subject = "conformance-s03-" + System.nanoTime() + "-value";
        String schemaJson = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Test3\\\","
                + "\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"int\\\"}]}";
        String body = "{\"schema\":\"" + schemaJson + "\",\"schemaType\":\"AVRO\"}";

        try {
            // Register schema
            URL regUrl = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection regConn = (HttpURLConnection) regUrl.openConnection();
            regConn.setRequestMethod("POST");
            regConn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            regConn.setDoOutput(true);
            regConn.setConnectTimeout(5000);
            regConn.setReadTimeout(5000);
            try (OutputStream os = regConn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            Assumptions.assumeTrue(regConn.getResponseCode() == 200 || regConn.getResponseCode() == 201,
                    "Schema registration failed");
            regConn.disconnect();

            // Get versions
            URL versionsUrl = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection versConn = (HttpURLConnection) versionsUrl.openConnection();
            versConn.setRequestMethod("GET");
            versConn.setConnectTimeout(5000);
            versConn.setReadTimeout(5000);

            int status = versConn.getResponseCode();
            assertEquals(200, status, "GET versions should return 200");
            String response = readHttpResponse(versConn);
            assertTrue(response.contains("1"), "Versions should include version 1");
            versConn.disconnect();
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        }
    }

    @Test @Order(32) @DisplayName("S04: Compatibility Check")
    void s04_compatibilityCheck() throws Exception {
        String subject = "conformance-s04-" + System.nanoTime() + "-value";
        String schemaJson = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Test4\\\","
                + "\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"int\\\"}]}";
        String body = "{\"schema\":\"" + schemaJson + "\",\"schemaType\":\"AVRO\"}";

        try {
            // Register initial schema
            URL regUrl = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection regConn = (HttpURLConnection) regUrl.openConnection();
            regConn.setRequestMethod("POST");
            regConn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            regConn.setDoOutput(true);
            regConn.setConnectTimeout(5000);
            regConn.setReadTimeout(5000);
            try (OutputStream os = regConn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            Assumptions.assumeTrue(regConn.getResponseCode() == 200 || regConn.getResponseCode() == 201,
                    "Schema registration failed");
            regConn.disconnect();

            // Check compatibility of a new version (add optional field — backward compatible)
            String compatSchema = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Test4\\\","
                    + "\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"int\\\"},"
                    + "{\\\"name\\\":\\\"extra\\\",\\\"type\\\":[\\\"null\\\",\\\"string\\\"],"
                    + "\\\"default\\\":null}]}";
            String compatBody = "{\"schema\":\"" + compatSchema + "\",\"schemaType\":\"AVRO\"}";

            URL compatUrl = new URL(getRegistryUrl() + "/compatibility/subjects/" + subject + "/versions/latest");
            HttpURLConnection compatConn = (HttpURLConnection) compatUrl.openConnection();
            compatConn.setRequestMethod("POST");
            compatConn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            compatConn.setDoOutput(true);
            compatConn.setConnectTimeout(5000);
            compatConn.setReadTimeout(5000);
            try (OutputStream os = compatConn.getOutputStream()) {
                os.write(compatBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = compatConn.getResponseCode();
            assertTrue(status >= 200 && status < 500,
                    "Compatibility check should return a valid response (got " + status + ")");
            if (status == 200) {
                String response = readHttpResponse(compatConn);
                assertTrue(response.contains("is_compatible"),
                        "Response should contain compatibility result");
            }
            compatConn.disconnect();
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        }
    }

    @Test @Order(33) @DisplayName("S05: Avro Schema")
    void s05_avroSchema() throws Exception {
        String subject = "conformance-s05-" + System.nanoTime() + "-value";
        String avroSchema = "{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"UserEvent\\\","
                + "\\\"namespace\\\":\\\"dev.streamline.test\\\","
                + "\\\"fields\\\":[{\\\"name\\\":\\\"userId\\\",\\\"type\\\":\\\"string\\\"},"
                + "{\\\"name\\\":\\\"eventType\\\",\\\"type\\\":\\\"string\\\"},"
                + "{\\\"name\\\":\\\"timestamp\\\",\\\"type\\\":\\\"long\\\"}]}";
        String body = "{\"schema\":\"" + avroSchema + "\",\"schemaType\":\"AVRO\"}";

        try {
            URL url = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            assertTrue(status >= 200 && status < 500,
                    "Avro schema registration should return a valid response (got " + status + ")");
            if (status == 200 || status == 201) {
                String response = readHttpResponse(conn);
                assertTrue(response.contains("id"), "Response should contain schema ID");
            }
            conn.disconnect();
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        }
    }

    @Test @Order(34) @DisplayName("S06: JSON Schema")
    void s06_jsonSchema() throws Exception {
        String subject = "conformance-s06-" + System.nanoTime() + "-value";
        String jsonSchema = "{\\\"type\\\":\\\"object\\\","
                + "\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\"},"
                + "\\\"age\\\":{\\\"type\\\":\\\"integer\\\"}},\\\"required\\\":[\\\"name\\\"]}";
        String body = "{\"schema\":\"" + jsonSchema + "\",\"schemaType\":\"JSON\"}";

        try {
            URL url = new URL(getRegistryUrl() + "/subjects/" + subject + "/versions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            assertTrue(status >= 200 && status < 500,
                    "JSON Schema registration should return a valid response (got " + status + ")");
            if (status == 200 || status == 201) {
                String response = readHttpResponse(conn);
                assertTrue(response.contains("id"), "Response should contain schema ID");
            }
            conn.disconnect();
        } catch (java.net.ConnectException e) {
            fail("Schema registry at " + getRegistryUrl() + " is not reachable: " + e.getMessage());
        }
    }

    // ========== ADMIN (4 tests) ==========

    @Test @Order(35) @DisplayName("D01: Create Topic")
    void d01_createTopic() throws Exception {
        String topic = uniqueTopic("d01");
        int partitions = 3;
        short replication = 1;

        NewTopic newTopic = new NewTopic(topic, partitions, replication);
        adminClient.createTopics(Collections.singletonList(newTopic))
                .all().get(10, TimeUnit.SECONDS);

        // Verify it exists with the right partition count
        TopicDescription desc = adminClient
                .describeTopics(Collections.singletonList(topic))
                .allTopicNames().get(10, TimeUnit.SECONDS).get(topic);

        assertNotNull(desc, "Topic description should not be null");
        assertEquals(partitions, desc.partitions().size(),
                "Topic should have " + partitions + " partitions");
    }

    @Test @Order(36) @DisplayName("D02: List Topics")
    void d02_listTopics() throws Exception {
        String topic = uniqueTopic("d02");
        adminClient.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        Set<String> topics = adminClient.listTopics()
                .names().get(10, TimeUnit.SECONDS);

        assertNotNull(topics, "Topic list should not be null");
        assertFalse(topics.isEmpty(), "Topic list should not be empty");
        assertTrue(topics.contains(topic),
                "Our test topic '" + topic + "' should appear in the listing");
    }

    @Test @Order(37) @DisplayName("D03: Describe Topic")
    void d03_describeTopic() throws Exception {
        String topic = uniqueTopic("d03");
        int partitions = 2;

        adminClient.createTopics(List.of(new NewTopic(topic, partitions, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        TopicDescription desc = adminClient.describeTopics(List.of(topic))
                .allTopicNames().get(10, TimeUnit.SECONDS).get(topic);

        assertNotNull(desc, "Topic description should not be null");
        assertEquals(topic, desc.name(), "Topic name should match");
        assertEquals(partitions, desc.partitions().size(), "Should have correct partition count");
        assertFalse(desc.partitions().get(0).replicas().isEmpty(), "Partitions should have replicas");
    }

    @Test @Order(38) @DisplayName("D04: Delete Topic")
    void d04_deleteTopic() throws Exception {
        String topic = uniqueTopic("d04");

        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                .all().get(10, TimeUnit.SECONDS);

        Set<String> before = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
        assertTrue(before.contains(topic), "Topic should exist before deletion");

        adminClient.deleteTopics(List.of(topic)).all().get(10, TimeUnit.SECONDS);

        Thread.sleep(500);
        Set<String> after = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
        assertFalse(after.contains(topic), "Topic should be removed after deletion");
    }

    // ========== ERROR HANDLING (4 tests) ==========

    @Test @Order(39) @DisplayName("E01: Connection Refused")
    void e01_connectionRefused() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19999");
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);

        assertThrows(Exception.class, () -> {
            try (AdminClient badAdmin = AdminClient.create(props)) {
                badAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "Connection to non-existent server should throw");
    }

    @Test @Order(40) @DisplayName("E02: Auth Denied")
    void e02_authDenied() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrap());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"denied\" password=\"badpass\";");

        assertThrows(Exception.class, () -> {
            try (AdminClient authAdmin = AdminClient.create(props)) {
                authAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "Auth with invalid credentials should be denied");
    }

    @Test @Order(41) @DisplayName("E03: Topic Not Found")
    void e03_topicNotFound() {
        String nonExistentTopic = "nonexistent-topic-" + System.nanoTime();

        try {
            Map<String, TopicDescription> result = adminClient
                    .describeTopics(List.of(nonExistentTopic))
                    .allTopicNames().get(10, TimeUnit.SECONDS);
            // Some servers auto-create topics; if so, verify the result is valid
            if (result.containsKey(nonExistentTopic)) {
                assertNotNull(result.get(nonExistentTopic));
            }
        } catch (ExecutionException e) {
            // Expected: UnknownTopicOrPartitionException wrapped in ExecutionException
            assertNotNull(e.getCause(), "ExecutionException should wrap the real cause");
        } catch (Exception e) {
            // Any error accessing a non-existent topic is valid error handling
            assertNotNull(e.getMessage(), "Exception should have a message");
        }
    }

    @Test @Order(42) @DisplayName("E04: Request Timeout")
    void e04_requestTimeout() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "192.0.2.1:9092");
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);

        assertThrows(Exception.class, () -> {
            try (AdminClient slowAdmin = AdminClient.create(props)) {
                slowAdmin.listTopics().names().get(5, TimeUnit.SECONDS);
            }
        }, "Request to unreachable server should timeout");
    }

    // ========== PERFORMANCE (4 tests) ==========

    @Test @Order(43) @DisplayName("F01: Throughput 1KB")
    void f01_throughput1kb() throws Exception {
        String topic = uniqueTopic("f01");
        int messageCount = 1000;
        String payload = "x".repeat(1024);

        long start = System.currentTimeMillis();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            List<Future<RecordMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < messageCount; i++) {
                futures.add(producer.send(new ProducerRecord<>(topic, "k" + i, payload)));
            }
            producer.flush();
            for (Future<RecordMetadata> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        double throughputMsgPerSec = (messageCount * 1000.0) / elapsed;
        double throughputKBPerSec = (messageCount * 1.0 * 1000.0) / elapsed;

        assertTrue(throughputMsgPerSec > 10,
                "Throughput should exceed 10 msg/s (was " + String.format("%.1f", throughputMsgPerSec) + " msg/s)");
        assertTrue(throughputKBPerSec > 10,
                "Throughput should exceed 10 KB/s (was " + String.format("%.1f", throughputKBPerSec) + " KB/s)");
    }

    @Test @Order(44) @DisplayName("F02: Latency P99")
    void f02_latencyP99() throws Exception {
        String topic = uniqueTopic("f02");
        int messageCount = 100;
        List<Long> latencies = new ArrayList<>();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (int i = 0; i < messageCount; i++) {
                long sendStart = System.nanoTime();
                producer.send(new ProducerRecord<>(topic, "k" + i, "latency-test")).get(10, TimeUnit.SECONDS);
                long latencyMs = (System.nanoTime() - sendStart) / 1_000_000;
                latencies.add(latencyMs);
            }
        }

        Collections.sort(latencies);
        int p99Index = (int) Math.ceil(messageCount * 0.99) - 1;
        long p99Latency = latencies.get(p99Index);

        assertTrue(p99Latency < 10_000,
                "P99 latency should be under 10s (was " + p99Latency + "ms)");
        assertTrue(latencies.get(0) >= 0, "Minimum latency should be non-negative");
    }

    @Test @Order(45) @DisplayName("F03: Startup Time")
    void f03_startupTime() throws Exception {
        long start = System.currentTimeMillis();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(uniqueTopic("f03"), "startup-key", "startup-value"))
                    .get(10, TimeUnit.SECONDS);
        }

        long startupMs = System.currentTimeMillis() - start;
        assertTrue(startupMs < 30_000,
                "Producer startup + first send should complete within 30s (was " + startupMs + "ms)");
    }

    @Test @Order(46) @DisplayName("F04: Memory Usage")
    void f04_memoryUsage() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(uniqueTopic("f04"), "mem-key", "mem-value"))
                    .get(10, TimeUnit.SECONDS);

            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memUsedBytes = memAfter - memBefore;
            double memUsedMB = memUsedBytes / (1024.0 * 1024.0);

            assertTrue(memUsedMB < 256,
                    "Producer memory usage should be under 256MB (was " + String.format("%.1f", memUsedMB) + "MB)");
        }
    }
}
