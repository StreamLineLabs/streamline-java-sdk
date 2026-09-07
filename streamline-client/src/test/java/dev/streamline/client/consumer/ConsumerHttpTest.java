package dev.streamline.client.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.streamline.client.ConnectionPool;
import dev.streamline.client.StreamlineConfig;
import dev.streamline.client.producer.ProducerConfig;
import dev.streamline.testsupport.UnitTestEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumerHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HOSTILE_TEXT =
            "\"quoted\" \\\\path\nline\ttab \001 Unicode 雪 😀 "
                    + "injection: \"},\"admin\":true,{\"x\":\"";

    private HttpServer server;
    private ConnectionPool connectionPool;
    private Consumer<String, String> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (connectionPool != null) {
            connectionPool.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchRoundTripsHostileQueryAndEscapedResponseValue() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        byte[] response = JSON.writeValueAsBytes(Map.of(
                "hits", List.of(Map.of(
                        "partition", 2,
                        "offset", 17,
                        "score", 0.75,
                        "value", HOSTILE_TEXT))));
        startServer(requestBody, response);
        createConsumer();

        List<SearchResult> results = consumer.search("test-topic", HOSTILE_TEXT, 7);

        assertEquals(HOSTILE_TEXT, requestBody.get().path("query").asText());
        assertEquals(7, requestBody.get().path("k").asInt());
        assertEquals(1, results.size());
        assertEquals(HOSTILE_TEXT, results.get(0).value());
        assertEquals(2, results.get(0).partition());
        assertEquals(17, results.get(0).offset());
        assertEquals(0.75, results.get(0).score());
    }

    @Test
    void searchRejectsInvalidArgumentsBeforeSending() {
        createConsumer();

        assertThrows(NullPointerException.class, () -> consumer.search("test-topic", null, 1));
        assertThrows(IllegalArgumentException.class, () -> consumer.search("test-topic", "query", 0));
    }

    private void startServer(AtomicReference<JsonNode> requestBody, byte[] response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/topics/test-topic/search", (HttpExchange exchange) -> {
            requestBody.set(JSON.readTree(exchange.getRequestBody()));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private void createConsumer() {
        StreamlineConfig.Builder configBuilder = StreamlineConfig.builder()
                .bootstrapServers(UnitTestEndpoints.BOOTSTRAP_SERVERS);
        if (server != null) {
            configBuilder.httpEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        }
        StreamlineConfig config = configBuilder
                .producerConfig(ProducerConfig.defaults())
                .consumerConfig(ConsumerConfig.defaults())
                .build();
        connectionPool = new ConnectionPool(config);
        consumer = new Consumer<>(
                connectionPool,
                config,
                "test-topic",
                ConsumerConfig.builder().groupId("test-group").build());
    }
}
