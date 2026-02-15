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
}
