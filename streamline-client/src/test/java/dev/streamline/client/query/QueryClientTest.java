package dev.streamline.client.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HOSTILE_SQL =
            "select \"quoted\", '\\\\path'\nfrom events\twhere value = '\001雪😀' "
                    + "or payload = '\"},\"admin\":true,{\"x\":\"'";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void querySerializesHostileSqlAsJson() throws Exception {
        AtomicReference<JsonNode> requestBody = startServer("/api/v1/query");

        QueryClient client = new QueryClient(baseUrl());
        assertEquals("{}", client.query(HOSTILE_SQL, 1000, 25));

        assertEquals(HOSTILE_SQL, requestBody.get().path("sql").asText());
        assertEquals(1000, requestBody.get().path("timeout_ms").asLong());
        assertEquals(25, requestBody.get().path("max_rows").asInt());
        assertEquals("json", requestBody.get().path("format").asText());
    }

    @Test
    void explainSerializesHostileSqlAsJson() throws Exception {
        AtomicReference<JsonNode> requestBody = startServer("/api/v1/query/explain");

        QueryClient client = new QueryClient(baseUrl());
        assertEquals("{}", client.explain(HOSTILE_SQL));

        assertEquals(HOSTILE_SQL, requestBody.get().path("sql").asText());
    }

    @Test
    void queryRejectsInvalidArgumentsBeforeSending() {
        QueryClient client = new QueryClient("http://127.0.0.1:1");

        assertThrows(NullPointerException.class, () -> client.query(null));
        assertThrows(IllegalArgumentException.class, () -> client.query("select 1", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> client.query("select 1", 1, 0));
        assertThrows(NullPointerException.class, () -> client.explain(null));
    }

    private AtomicReference<JsonNode> startServer(String path) throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, (HttpExchange exchange) -> {
            requestBody.set(JSON.readTree(exchange.getRequestBody()));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return requestBody;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
