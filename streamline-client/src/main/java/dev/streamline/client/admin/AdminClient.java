package dev.streamline.client.admin;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.StreamlineException;
import dev.streamline.client.TopicNameValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Administrative client for managing Streamline topics, consumer groups, and cluster metadata.
 *
 * <p>Delegates to the Apache Kafka {@link Admin} client for wire protocol compatibility.
 *
 * <p>Example usage:
 * <pre>{@code
 * Streamline client = Streamline.builder()
 *     .bootstrapServers("localhost:9092")
 *     .build();
 *
 * try (AdminClient admin = client.admin()) {
 *     admin.createTopic("my-topic", 3, (short) 1);
 *     Set<String> topics = admin.listTopics();
 *     System.out.println("Topics: " + topics);
 * }
 * }</pre>
 */
public class AdminClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(AdminClient.class);

    private final ConnectionPool connectionPool;
    private final StreamlineConfig config;
    private final Admin kafkaAdmin;
    private volatile boolean closed = false;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String httpUrl;
    private final HttpClient httpClient;

    public AdminClient(ConnectionPool connectionPool, StreamlineConfig config) {
        this.connectionPool = Objects.requireNonNull(connectionPool, "connectionPool must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, config.getRequestTimeoutMs());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, config.getRequestTimeoutMs());

        this.kafkaAdmin = Admin.create(props);
        this.httpUrl = config.getHttpEndpoint();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.debug("AdminClient created for bootstrap servers: {}", config.getBootstrapServers());
    }

    // -- Topic operations ------------------------------------------------

    /**
     * Creates a new topic with the specified number of partitions and replication factor.
     *
     * @param name              the topic name
     * @param partitions        the number of partitions
     * @param replicationFactor the replication factor
     * @throws StreamlineException if topic creation fails
     */
    public void createTopic(String name, int partitions, short replicationFactor) {
        ensureOpen();
        TopicNameValidator.validate(name);

        NewTopic newTopic = new NewTopic(name, partitions, replicationFactor);
        try {
            kafkaAdmin.createTopics(Collections.singleton(newTopic)).all().get();
            log.info("Created topic '{}' with {} partitions and replication factor {}",
                    name, partitions, replicationFactor);
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to create topic: " + name, e.getCause(), false,
                    "Check that the topic does not already exist and the server is reachable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while creating topic: " + name, e);
        }
    }

    /**
     * Deletes the specified topic.
     *
     * @param name the topic name
     * @throws StreamlineException if topic deletion fails
     */
    public void deleteTopic(String name) {
        ensureOpen();
        TopicNameValidator.validate(name);

        try {
            kafkaAdmin.deleteTopics(Collections.singleton(name)).all().get();
            log.info("Deleted topic '{}'", name);
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to delete topic: " + name, e.getCause(), false,
                    "Check that the topic exists and the server is reachable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while deleting topic: " + name, e);
        }
    }

    /**
     * Lists all topic names in the cluster.
     *
     * @return a set of topic names
     * @throws StreamlineException if listing topics fails
     */
    public Set<String> listTopics() {
        ensureOpen();
        try {
            ListTopicsResult result = kafkaAdmin.listTopics();
            Set<String> topics = result.names().get();
            log.debug("Listed {} topics", topics.size());
            return topics;
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to list topics", e.getCause(), true,
                    "Check that the Streamline server is running and accessible.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while listing topics", e);
        }
    }

    /**
     * Describes the specified topic, returning partition and replica information.
     *
     * @param name the topic name
     * @return the topic description
     * @throws StreamlineException if describing the topic fails
     */
    public TopicDescription describeTopic(String name) {
        ensureOpen();
        TopicNameValidator.validate(name);

        try {
            DescribeTopicsResult result = kafkaAdmin.describeTopics(Collections.singleton(name));
            TopicDescription description = result.topicNameValues().get(name).get();
            log.debug("Described topic '{}': {} partitions", name, description.partitions().size());
            return description;
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to describe topic: " + name, e.getCause(), false,
                    "Check that the topic exists. Create it with: streamline-cli topics create " + name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while describing topic: " + name, e);
        }
    }

    // -- Consumer group operations ---------------------------------------

    /**
     * Lists all consumer groups in the cluster.
     *
     * @return a collection of consumer group listings
     * @throws StreamlineException if listing consumer groups fails
     */
    public Collection<ConsumerGroupListing> listConsumerGroups() {
        ensureOpen();
        try {
            ListConsumerGroupsResult result = kafkaAdmin.listConsumerGroups();
            Collection<ConsumerGroupListing> groups = result.all().get();
            log.debug("Listed {} consumer groups", groups.size());
            return groups;
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to list consumer groups", e.getCause(), true,
                    "Check that the Streamline server is running and accessible.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while listing consumer groups", e);
        }
    }

    /**
     * Describes the specified consumer group.
     *
     * @param groupId the consumer group ID
     * @return the consumer group description
     * @throws StreamlineException if describing the consumer group fails
     */
    public ConsumerGroupDescription describeConsumerGroup(String groupId) {
        ensureOpen();
        if (groupId == null) {
            throw new IllegalArgumentException("Consumer group ID must not be null");
        }

        try {
            DescribeConsumerGroupsResult result =
                    kafkaAdmin.describeConsumerGroups(Collections.singleton(groupId));
            ConsumerGroupDescription description = result.describedGroups().get(groupId).get();
            log.debug("Described consumer group '{}': state={}", groupId, description.state());
            return description;
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to describe consumer group: " + groupId, e.getCause(), false,
                    "Check that the consumer group exists and the server is reachable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while describing consumer group: " + groupId, e);
        }
    }

    /**
     * Deletes the specified consumer group.
     *
     * @param groupId the consumer group ID
     * @throws StreamlineException if deleting the consumer group fails
     */
    public void deleteConsumerGroup(String groupId) {
        ensureOpen();
        if (groupId == null) {
            throw new IllegalArgumentException("Consumer group ID must not be null");
        }

        try {
            kafkaAdmin.deleteConsumerGroups(Collections.singleton(groupId)).all().get();
            log.info("Deleted consumer group '{}'", groupId);
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to delete consumer group: " + groupId, e.getCause(), false,
                    "Ensure the group has no active members before deleting.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while deleting consumer group: " + groupId, e);
        }
    }

    // -- Cluster operations ----------------------------------------------

    /**
     * Returns the nodes that make up the cluster.
     *
     * @return a collection of cluster nodes
     * @throws StreamlineException if describing the cluster fails
     */
    public Collection<Node> describeCluster() {
        ensureOpen();
        try {
            DescribeClusterResult result = kafkaAdmin.describeCluster();
            Collection<Node> nodes = result.nodes().get();
            log.debug("Cluster has {} nodes", nodes.size());
            return nodes;
        } catch (ExecutionException e) {
            throw new StreamlineException(
                    "Failed to describe cluster", e.getCause(), true,
                    "Check that the Streamline server is running and accessible.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while describing cluster", e);
        }
    }

    // -- Branch operations (M5, Experimental) --------------------------------

    /**
     * Information about a copy-on-write topic branch (M5, Experimental).
     *
     * @param name       Branch name
     * @param baseTopic  The base topic this branch forks from
     * @param state      Branch state ({@code "active"}, {@code "discarded"}, {@code "merged"})
     * @param createdAt  Creation timestamp (epoch milliseconds)
     */
    public record BranchInfo(String name, String baseTopic, String state, long createdAt) {}

    /**
     * Creates a copy-on-write branch of a topic.
     *
     * @param name        branch name
     * @param baseTopic   topic to branch from
     * @param baseOffsets per-partition base offsets (may be {@code null})
     * @return information about the created branch
     * @throws StreamlineException if the HTTP request fails
     */
    public BranchInfo createBranch(String name, String baseTopic, Map<Integer, Long> baseOffsets) {
        ensureOpen();
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(baseTopic, "baseTopic must not be null");
        try {
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("name", name);
            body.put("base_topic", baseTopic);
            if (baseOffsets != null && !baseOffsets.isEmpty()) {
                body.put("base_offsets", baseOffsets);
            }
            byte[] json = MAPPER.writeValueAsBytes(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(httpUrl + "/api/v1/branches"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new StreamlineException("Failed to create branch: HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode node = MAPPER.readTree(resp.body());
            return new BranchInfo(
                    node.path("name").asText(name),
                    node.path("base_topic").asText(baseTopic),
                    node.path("state").asText("active"),
                    node.path("created_at").asLong(0));
        } catch (StreamlineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while creating branch: " + name, e);
        } catch (Exception e) {
            throw new StreamlineException("Failed to create branch: " + name, e);
        }
    }

    /**
     * Lists copy-on-write topic branches.
     *
     * @param topic filter by base topic (may be {@code null} for all)
     * @return list of branch info objects
     * @throws StreamlineException if the HTTP request fails
     */
    public List<BranchInfo> listBranches(String topic) {
        ensureOpen();
        try {
            String path = "/api/v1/branches";
            if (topic != null && !topic.isEmpty()) {
                path += "?topic=" + java.net.URLEncoder.encode(topic, StandardCharsets.UTF_8);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(httpUrl + path))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new StreamlineException("Failed to list branches: HTTP " + resp.statusCode());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode arr = root.isArray() ? root : root.path("items");
            List<BranchInfo> result = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    result.add(new BranchInfo(
                            n.path("name").asText(""),
                            n.path("base_topic").asText(""),
                            n.path("state").asText("active"),
                            n.path("created_at").asLong(0)));
                }
            }
            return result;
        } catch (StreamlineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while listing branches", e);
        } catch (Exception e) {
            throw new StreamlineException("Failed to list branches", e);
        }
    }

    /**
     * Discards (deletes) a copy-on-write topic branch.
     *
     * @param branchId branch identifier
     * @throws StreamlineException if the HTTP request fails
     */
    public void discardBranch(String branchId) {
        ensureOpen();
        Objects.requireNonNull(branchId, "branchId must not be null");
        try {
            String path = "/api/v1/branches/" + java.net.URLEncoder.encode(branchId, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(httpUrl + path))
                    .DELETE()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new StreamlineException("Failed to discard branch: HTTP " + resp.statusCode() + ": " + resp.body());
            }
            log.info("Discarded branch '{}'", branchId);
        } catch (StreamlineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while discarding branch: " + branchId, e);
        } catch (Exception e) {
            throw new StreamlineException("Failed to discard branch: " + branchId, e);
        }
    }

    // -- Lifecycle -------------------------------------------------------

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("AdminClient is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            kafkaAdmin.close(Duration.ofSeconds(30));
            log.debug("AdminClient closed");
        }
    }
}
