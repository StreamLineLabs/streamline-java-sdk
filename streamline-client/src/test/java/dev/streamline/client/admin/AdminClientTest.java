package dev.streamline.client.admin;

import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.testsupport.UnitTestEndpoints;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteConsumerGroupsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AdminClientTest {

    private ConnectionPool connectionPool;
    private StreamlineConfig config;
    private Admin mockKafkaAdmin;
    private Admin realKafkaAdmin;
    private AdminClient adminClient;

    @BeforeEach
    void setUp() throws Exception {
        config = new StreamlineConfig(
                UnitTestEndpoints.BOOTSTRAP_SERVERS,
                ProducerConfig.defaults(),
                ConsumerConfig.defaults(),
                4, 30000, 30000
        );
        connectionPool = new ConnectionPool(config);

        // Create AdminClient, then replace internal kafkaAdmin with a mock. The real
        // client the constructor built is kept so it can be shut down afterwards.
        adminClient = new AdminClient(connectionPool, config);
        realKafkaAdmin = (Admin) getField(adminClient, "kafkaAdmin");
        mockKafkaAdmin = mock(Admin.class);
        setField(adminClient, "kafkaAdmin", mockKafkaAdmin);
    }

    @AfterEach
    void tearDown() {
        if (adminClient != null) {
            // Reset to avoid calling close on mock without stubbing
            try {
                setField(adminClient, "closed", true);
            } catch (Exception ignored) {
            }
        }
        if (realKafkaAdmin != null) {
            realKafkaAdmin.close(Duration.ZERO);
        }
        connectionPool.close();
    }

    @Test
    void testCreateAdminClient() {
        assertNotNull(adminClient);
    }

    @Test
    void testListTopicsReturnsSet() throws Exception {
        Set<String> expected = new HashSet<>(Set.of("topic-a", "topic-b"));
        ListTopicsResult listResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> future = new KafkaFutureImpl<>();
        future.complete(expected);
        when(listResult.names()).thenReturn(future);
        when(mockKafkaAdmin.listTopics()).thenReturn(listResult);

        Set<String> topics = adminClient.listTopics();

        assertNotNull(topics);
        assertInstanceOf(Set.class, topics);
        assertEquals(expected, topics);
    }

    @Test
    void testCreateTopicWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminClient.createTopic(null, 1, (short) 1));
    }

    @Test
    void testDeleteTopicWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminClient.deleteTopic(null));
    }

    @Test
    void testDescribeConsumerGroupWithNullGroupIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminClient.describeConsumerGroup(null));
    }

    @Test
    void testDeleteConsumerGroupWithNullGroupIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminClient.deleteConsumerGroup(null));
    }

    @Test
    void testDescribeTopicWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminClient.describeTopic(null));
    }

    @Test
    void testCloseCanBeCalledMultipleTimes() {
        doNothing().when(mockKafkaAdmin).close(any());
        try {
            setField(adminClient, "closed", false);
        } catch (Exception ignored) {
        }

        assertDoesNotThrow(() -> {
            adminClient.close();
            adminClient.close();
        });
    }

    @Test
    void testOperationsAfterCloseThrow() {
        doNothing().when(mockKafkaAdmin).close(any());
        adminClient.close();

        assertThrows(IllegalStateException.class, () -> adminClient.listTopics());
        assertThrows(IllegalStateException.class, () -> adminClient.createTopic("t", 1, (short) 1));
        assertThrows(IllegalStateException.class, () -> adminClient.deleteTopic("t"));
        assertThrows(IllegalStateException.class, () -> adminClient.describeTopic("t"));
        assertThrows(IllegalStateException.class, () -> adminClient.listConsumerGroups());
        assertThrows(IllegalStateException.class, () -> adminClient.describeConsumerGroup("g"));
        assertThrows(IllegalStateException.class, () -> adminClient.deleteConsumerGroup("g"));
        assertThrows(IllegalStateException.class, () -> adminClient.describeCluster());
    }

    @Test
    void testCreateTopicDelegatesToKafkaAdmin() throws Exception {
        CreateTopicsResult createResult = mock(CreateTopicsResult.class);
        KafkaFutureImpl<Void> future = new KafkaFutureImpl<>();
        future.complete(null);
        when(createResult.all()).thenReturn(future);
        when(mockKafkaAdmin.createTopics(anyCollection())).thenReturn(createResult);

        assertDoesNotThrow(() -> adminClient.createTopic("new-topic", 3, (short) 1));
        verify(mockKafkaAdmin).createTopics(anyCollection());
    }

    @Test
    void testDeleteTopicDelegatesToKafkaAdmin() throws Exception {
        DeleteTopicsResult deleteResult = mock(DeleteTopicsResult.class);
        KafkaFutureImpl<Void> future = new KafkaFutureImpl<>();
        future.complete(null);
        when(deleteResult.all()).thenReturn(future);
        when(mockKafkaAdmin.deleteTopics(anyCollection())).thenReturn(deleteResult);

        assertDoesNotThrow(() -> adminClient.deleteTopic("old-topic"));
        verify(mockKafkaAdmin).deleteTopics(anyCollection());
    }

    @Test
    void testDescribeTopicDelegatesToKafkaAdmin() throws Exception {
        Node leader = new Node(0, "localhost", 9092);
        TopicPartitionInfo partitionInfo = new TopicPartitionInfo(
                0, leader, List.of(leader), List.of(leader));
        TopicDescription description = new TopicDescription(
                "my-topic", false, List.of(partitionInfo));

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<TopicDescription> future = new KafkaFutureImpl<>();
        future.complete(description);
        when(describeResult.topicNameValues()).thenReturn(Map.of("my-topic", future));
        when(mockKafkaAdmin.describeTopics(anyCollection())).thenReturn(describeResult);

        TopicDescription result = adminClient.describeTopic("my-topic");

        assertNotNull(result);
        assertEquals("my-topic", result.name());
        assertEquals(1, result.partitions().size());
    }

    @Test
    void testDescribeClusterDelegatesToKafkaAdmin() throws Exception {
        Node node = new Node(0, "localhost", 9092);
        DescribeClusterResult clusterResult = mock(DescribeClusterResult.class);
        KafkaFutureImpl<java.util.Collection<Node>> future = new KafkaFutureImpl<>();
        future.complete(List.of(node));
        when(clusterResult.nodes()).thenReturn(future);
        when(mockKafkaAdmin.describeCluster()).thenReturn(clusterResult);

        java.util.Collection<Node> nodes = adminClient.describeCluster();

        assertNotNull(nodes);
        assertEquals(1, nodes.size());
    }

    @Test
    void testConstructorWithNullConnectionPoolThrows() {
        assertThrows(NullPointerException.class,
                () -> new AdminClient(null, config));
    }

    @Test
    void testConstructorWithNullConfigThrows() {
        assertThrows(NullPointerException.class,
                () -> new AdminClient(connectionPool, null));
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
