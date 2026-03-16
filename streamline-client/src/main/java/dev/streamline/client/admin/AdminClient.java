package dev.streamline.client.admin;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.StreamlineException;
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
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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

    public AdminClient(ConnectionPool connectionPool, StreamlineConfig config) {
        this.connectionPool = Objects.requireNonNull(connectionPool, "connectionPool must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, config.getRequestTimeoutMs());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, config.getRequestTimeoutMs());

        this.kafkaAdmin = Admin.create(props);
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
        if (name == null) {
            throw new IllegalArgumentException("Topic name must not be null");
        }

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
        if (name == null) {
            throw new IllegalArgumentException("Topic name must not be null");
        }

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
        if (name == null) {
            throw new IllegalArgumentException("Topic name must not be null");
        }

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
