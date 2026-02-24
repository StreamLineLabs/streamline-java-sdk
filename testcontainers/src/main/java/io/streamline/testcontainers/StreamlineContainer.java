package io.streamline.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Testcontainers module for Streamline - The Redis of Streaming.
 *
 * <p>Streamline is a Kafka-compatible streaming platform that provides a lightweight,
 * single-binary alternative to Apache Kafka for development and testing.</p>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * @Container
 * StreamlineContainer streamline = new StreamlineContainer();
 *
 * // Get bootstrap servers for Kafka clients
 * String bootstrapServers = streamline.getBootstrapServers();
 *
 * // Use with any Kafka client
 * Properties props = new Properties();
 * props.put("bootstrap.servers", bootstrapServers);
 * props.put("key.serializer", StringSerializer.class.getName());
 * props.put("value.serializer", StringSerializer.class.getName());
 * KafkaProducer<String, String> producer = new KafkaProducer<>(props);
 * }</pre>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Kafka protocol compatible - use existing Kafka clients unchanged</li>
 *   <li>Fast startup (~100ms vs seconds for Kafka)</li>
 *   <li>Low memory footprint (&lt;50MB)</li>
 *   <li>No ZooKeeper or KRaft required</li>
 *   <li>Built-in HTTP API for health checks and metrics</li>
 * </ul>
 */
public class StreamlineContainer extends GenericContainer<StreamlineContainer> {

    /** Default Docker image name */
    public static final String DEFAULT_IMAGE = "ghcr.io/streamlinelabs/streamline";

    /** Default image tag */
    public static final String DEFAULT_TAG = "latest";

    /** Kafka protocol port */
    public static final int KAFKA_PORT = 9092;

    /** HTTP API port */
    public static final int HTTP_PORT = 9094;

    /** Default startup timeout */
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(30);

    private String logLevel = "info";

    /**
     * Creates a new Streamline container with the default image.
     */
    public StreamlineContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE).withTag(DEFAULT_TAG));
    }

    /**
     * Creates a new Streamline container with a specific image tag.
     *
     * @param tag the Docker image tag to use
     */
    public StreamlineContainer(String tag) {
        this(DockerImageName.parse(DEFAULT_IMAGE).withTag(tag));
    }

    /**
     * Creates a new Streamline container with a specific Docker image.
     *
     * @param dockerImageName the Docker image to use
     */
    public StreamlineContainer(DockerImageName dockerImageName) {
        super(dockerImageName);

        withExposedPorts(KAFKA_PORT, HTTP_PORT);
        withEnv("STREAMLINE_LISTEN_ADDR", "0.0.0.0:" + KAFKA_PORT);
        withEnv("STREAMLINE_HTTP_ADDR", "0.0.0.0:" + HTTP_PORT);

        // Wait for health endpoint to be available
        waitingFor(Wait.forHttp("/health")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
    }

    /**
     * Returns the Kafka bootstrap servers connection string.
     *
     * <p>Use this value for the {@code bootstrap.servers} property in Kafka clients.</p>
     *
     * @return the bootstrap servers string in format "host:port"
     */
    public String getBootstrapServers() {
        return String.format("%s:%d", getHost(), getMappedPort(KAFKA_PORT));
    }

    /**
     * Returns the HTTP API base URL.
     *
     * <p>The HTTP API provides endpoints for health checks, metrics, and administration.</p>
     *
     * @return the HTTP API base URL
     */
    public String getHttpUrl() {
        return String.format("http://%s:%d", getHost(), getMappedPort(HTTP_PORT));
    }

    /**
     * Returns the health check endpoint URL.
     *
     * @return the health endpoint URL
     */
    public String getHealthUrl() {
        return getHttpUrl() + "/health";
    }

    /**
     * Returns the metrics endpoint URL.
     *
     * <p>Prometheus-compatible metrics are available at this endpoint.</p>
     *
     * @return the metrics endpoint URL
     */
    public String getMetricsUrl() {
        return getHttpUrl() + "/metrics";
    }

    /**
     * Sets the log level for the Streamline server.
     *
     * @param level the log level (trace, debug, info, warn, error)
     * @return this container instance for method chaining
     */
    public StreamlineContainer withLogLevel(String level) {
        this.logLevel = level;
        withEnv("STREAMLINE_LOG_LEVEL", level);
        return this;
    }

    /**
     * Enables debug logging.
     *
     * @return this container instance for method chaining
     */
    public StreamlineContainer withDebugLogging() {
        return withLogLevel("debug");
    }

    /**
     * Enables trace logging.
     *
     * @return this container instance for method chaining
     */
    public StreamlineContainer withTraceLogging() {
        return withLogLevel("trace");
    }

    /**
     * Creates a topic with the specified name and number of partitions.
     *
     * <p>Note: Streamline supports auto-topic creation, so topics are created
     * automatically when a producer first writes to them. Use this method
     * if you need to pre-create topics with specific configurations.</p>
     *
     * @param topicName the name of the topic to create
     * @param partitions the number of partitions
     * @throws RuntimeException if topic creation fails
     */
    public void createTopic(String topicName, int partitions) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli",
                "topics", "create", topicName,
                "--partitions", String.valueOf(partitions)
            );
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Failed to create topic: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create topic: " + topicName, e);
        }
    }

    /**
     * Creates a topic with the specified name and default configuration (1 partition).
     *
     * @param topicName the name of the topic to create
     */
    public void createTopic(String topicName) {
        createTopic(topicName, 1);
    }

    /**
     * Creates multiple topics at once.
     *
     * @param topics map of topic name to partition count
     * @throws RuntimeException if any topic creation fails
     */
    public void createTopics(java.util.Map<String, Integer> topics) {
        topics.forEach(this::createTopic);
    }

    /**
     * Produces a single message to a topic.
     *
     * @param topic the topic name
     * @param value the message value
     * @throws RuntimeException if message production fails
     */
    public void produceMessage(String topic, String value) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "produce", topic, "-m", value
            );
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Failed to produce message: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to produce message to " + topic, e);
        }
    }

    /**
     * Produces a keyed message to a topic.
     *
     * @param topic the topic name
     * @param key the message key
     * @param value the message value
     * @throws RuntimeException if message production fails
     */
    public void produceMessage(String topic, String key, String value) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "produce", topic, "-m", value, "-k", key
            );
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Failed to produce keyed message: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to produce keyed message to " + topic, e);
        }
    }

    /**
     * Enables in-memory storage mode (no disk persistence).
     *
     * @return this container instance for method chaining
     */
    public StreamlineContainer withInMemory() {
        withEnv("STREAMLINE_IN_MEMORY", "true");
        return this;
    }

    /**
     * Enables playground mode (pre-loaded demo topics).
     *
     * @return this container instance for method chaining
     */
    public StreamlineContainer withPlayground() {
        withEnv("STREAMLINE_PLAYGROUND", "true");
        return this;
    }

    /**
     * Returns the server info endpoint URL.
     *
     * @return the info endpoint URL
     */
    public String getInfoUrl() {
        return getHttpUrl() + "/info";
    }

    /**
     * Asserts that the container is healthy by calling the health endpoint.
     *
     * @throws AssertionError if the container is not healthy
     */
    public void assertHealthy() {
        try {
            java.net.URL url = new java.net.URL(getHealthUrl());
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                throw new AssertionError("Health check returned status " + statusCode);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Health check failed: " + e.getMessage());
        }
    }

    /**
     * Asserts that a topic exists on the server.
     *
     * @param topicName the topic name to verify
     * @throws AssertionError if the topic does not exist
     */
    public void assertTopicExists(String topicName) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "topics", "describe", topicName
            );
            if (result.getExitCode() != 0) {
                throw new AssertionError("Topic '" + topicName + "' does not exist");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to check topic: " + e.getMessage());
        }
    }

    /**
     * Waits until all specified topics exist (with timeout).
     *
     * @param topics list of topic names to wait for
     * @param timeout maximum time to wait
     * @throws RuntimeException if topics don't appear within the timeout
     */
    public void waitForTopics(java.util.List<String> topics, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            boolean allExist = true;
            for (String topic : topics) {
                try {
                    ExecResult result = execInContainer(
                        "streamline-cli", "topics", "describe", topic
                    );
                    if (result.getExitCode() != 0) {
                        allExist = false;
                        break;
                    }
                } catch (Exception e) {
                    allExist = false;
                    break;
                }
            }
            if (allExist) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for topics");
            }
        }
        throw new RuntimeException("Timeout waiting for topics after " + timeout);
    }

    // -------------------------------------------------------------------------
    // Enhanced capabilities: batch produce, consumer groups, migration helpers
    // -------------------------------------------------------------------------

    /**
     * Produces a batch of messages to a topic.
     *
     * @param topic the topic name
     * @param messages list of messages to produce
     * @throws RuntimeException if batch production fails
     */
    public void produceMessages(String topic, java.util.List<String> messages) {
        for (String msg : messages) {
            produceMessage(topic, msg);
        }
    }

    /**
     * Produces a batch of keyed messages to a topic.
     *
     * @param topic the topic name
     * @param messages map of key to value
     * @throws RuntimeException if batch production fails
     */
    public void produceKeyedMessages(String topic, java.util.Map<String, String> messages) {
        messages.forEach((key, value) -> produceMessage(topic, key, value));
    }

    /**
     * Lists consumer groups via the CLI.
     *
     * @return list of consumer group IDs
     */
    public java.util.List<String> listConsumerGroups() {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "groups", "list", "--format", "json"
            );
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Failed to list consumer groups: " + result.getStderr());
            }
            String output = result.getStdout().trim();
            java.util.List<String> groups = new java.util.ArrayList<>();
            if (!output.isEmpty()) {
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("[") && !trimmed.startsWith("]")) {
                        groups.add(trimmed.replaceAll("[\"',]", ""));
                    }
                }
            }
            return groups;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list consumer groups", e);
        }
    }

    /**
     * Asserts that a consumer group exists.
     *
     * @param groupId the consumer group ID
     * @throws AssertionError if the group does not exist
     */
    public void assertConsumerGroupExists(String groupId) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "groups", "describe", groupId
            );
            if (result.getExitCode() != 0) {
                throw new AssertionError("Consumer group '" + groupId + "' does not exist");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to check consumer group: " + e.getMessage());
        }
    }

    /**
     * Gets the partition count for a topic.
     *
     * @param topicName the topic name
     * @return the number of partitions
     */
    public int getPartitionCount(String topicName) {
        try {
            ExecResult result = execInContainer(
                "streamline-cli", "topics", "describe", topicName, "--format", "json"
            );
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Topic '" + topicName + "' not found");
            }
            String output = result.getStdout();
            // Parse partition count from JSON output
            int idx = output.indexOf("partitions");
            if (idx >= 0) {
                String after = output.substring(idx);
                StringBuilder num = new StringBuilder();
                boolean foundColon = false;
                for (char c : after.toCharArray()) {
                    if (c == ':') foundColon = true;
                    else if (foundColon && Character.isDigit(c)) num.append(c);
                    else if (foundColon && num.length() > 0) break;
                }
                if (num.length() > 0) return Integer.parseInt(num.toString());
            }
            return 1;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get partition count", e);
        }
    }

    /**
     * Asserts that a topic has the expected number of partitions.
     *
     * @param topicName the topic name
     * @param expectedPartitions the expected partition count
     * @throws AssertionError if the partition count doesn't match
     */
    public void assertPartitionCount(String topicName, int expectedPartitions) {
        int actual = getPartitionCount(topicName);
        if (actual != expectedPartitions) {
            throw new AssertionError(
                "Expected " + expectedPartitions + " partitions for topic '" +
                topicName + "', but found " + actual
            );
        }
    }

    /**
     * Gets cluster information from the HTTP API.
     *
     * @return the cluster info as a JSON string
     */
    public String getClusterInfo() {
        try {
            java.net.URL url = new java.net.URL(getInfoUrl());
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            java.io.InputStream is = conn.getInputStream();
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get cluster info: " + e.getMessage(), e);
        }
    }

    /**
     * Enables auto-topic creation with default partition count.
     *
     * @param defaultPartitions default number of partitions for auto-created topics
     * @return this container instance for method chaining
     */
    public StreamlineContainer withAutoCreateTopics(int defaultPartitions) {
        withEnv("STREAMLINE_AUTO_CREATE_TOPICS", "true");
        withEnv("STREAMLINE_DEFAULT_PARTITIONS", String.valueOf(defaultPartitions));
        return this;
    }

    /**
     * Enables authentication with SASL/PLAIN.
     *
     * @param username the admin username
     * @param password the admin password
     * @return this container instance for method chaining
     */
    public StreamlineContainer withAuthentication(String username, String password) {
        withEnv("STREAMLINE_AUTH_ENABLED", "true");
        withEnv("STREAMLINE_AUTH_DEFAULT_USER", username);
        withEnv("STREAMLINE_AUTH_DEFAULT_PASSWORD", password);
        return this;
    }

    // -------------------------------------------------------------------------
    // Kafka migration helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a Streamline container configured as a drop-in replacement for
     * the Testcontainers KafkaContainer. Useful for migrating from Kafka-based tests.
     *
     * <p>Usage (migration from Kafka):</p>
     * <pre>{@code
     * // Before (Kafka):
     * // KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
     *
     * // After (Streamline):
     * StreamlineContainer streamline = StreamlineContainer.asKafkaReplacement();
     * String bootstrapServers = streamline.getBootstrapServers();
     * // Rest of your Kafka client code works unchanged
     * }</pre>
     *
     * @return a new StreamlineContainer configured for Kafka compatibility
     */
    public static StreamlineContainer asKafkaReplacement() {
        return new StreamlineContainer()
                .withInMemory()
                .withAutoCreateTopics(1);
    }

    /**
     * Creates a container pre-configured with topics matching a Kafka test setup.
     *
     * @param topics map of topic name to partition count
     * @return a new StreamlineContainer with pre-configured topics
     */
    public static StreamlineContainer withPreConfiguredTopics(java.util.Map<String, Integer> topics) {
        StreamlineContainer container = new StreamlineContainer().withInMemory();
        for (java.util.Map.Entry<String, Integer> entry : topics.entrySet()) {
            container.withEnv(
                "STREAMLINE_AUTO_TOPIC_" + entry.getKey(),
                String.valueOf(entry.getValue())
            );
        }
        return container;
    }

    /**
     * Enables ephemeral mode: in-memory, auto-cleanup, fastest possible startup.
     * The server will auto-shutdown after the idle timeout if no clients are connected.
     *
     * @return this container instance for method chaining
     */
    public StreamlineContainer withEphemeral() {
        withEnv("STREAMLINE_EPHEMERAL", "true");
        withEnv("STREAMLINE_IN_MEMORY", "true");
        return this;
    }

    /**
     * Sets the ephemeral idle timeout (how long to wait with zero connections before shutdown).
     *
     * @param seconds seconds to wait before auto-shutdown
     * @return this container instance for method chaining
     */
    public StreamlineContainer withEphemeralIdleTimeout(int seconds) {
        withEnv("STREAMLINE_EPHEMERAL_IDLE_TIMEOUT", String.valueOf(seconds));
        return this;
    }

    /**
     * Pre-configures topics to be auto-created on startup in ephemeral mode.
     *
     * @param topicSpecs comma-separated list of "name:partitions" specs
     * @return this container instance for method chaining
     */
    public StreamlineContainer withEphemeralAutoTopics(String topicSpecs) {
        withEnv("STREAMLINE_EPHEMERAL_AUTO_TOPICS", topicSpecs);
        return this;
    }

    /**
     * Creates a container optimized for CI/CD testing:
     * ephemeral mode, in-memory, auto-create topics, minimal logging.
     *
     * <p>Usage:</p>
     * <pre>{@code
     * @Container
     * StreamlineContainer streamline = StreamlineContainer.forTesting()
     *     .withEphemeralAutoTopics("orders:3,events:6,logs:1");
     * }</pre>
     *
     * @return a new StreamlineContainer optimized for testing
     */
    public static StreamlineContainer forTesting() {
        return new StreamlineContainer()
                .withEphemeral()
                .withAutoCreateTopics(3)
                .withLogLevel("warn");
    }
}
