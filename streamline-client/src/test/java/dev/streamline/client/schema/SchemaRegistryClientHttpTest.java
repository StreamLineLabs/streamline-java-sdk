package dev.streamline.client.schema;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaRegistryClientHttpTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registerSchemaEncodesSubjectAsOnePathSegment() throws Exception {
        AtomicReference<String> rawPath = startServer("{\"id\":42}");
        SchemaRegistryClient client = new SchemaRegistryClient(baseUrl());

        assertEquals(42, client.registerSchema(
                "../a/b c%?#雪😀",
                "{\"type\":\"string\"}",
                SchemaFormat.JSON));

        assertEquals(
                "/subjects/..%2Fa%2Fb%20c%25%3F%23%E9%9B%AA%F0%9F%98%80/versions",
                rawPath.get());
        assertFalse(rawPath.get().contains("+"));
    }

    @Test
    void numericPathSegmentsMustBePositive() {
        SchemaRegistryClient client = new SchemaRegistryClient("http://127.0.0.1:1");

        assertThrows(IllegalArgumentException.class, () -> client.getSchema("subject", 0));
        assertThrows(IllegalArgumentException.class, () -> client.getSchema(0));
        assertThrows(IllegalArgumentException.class,
                () -> client.registerSchema("", "{}", SchemaFormat.JSON));
    }

    private AtomicReference<String> startServer(String body) throws Exception {
        AtomicReference<String> rawPath = new AtomicReference<>();
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange exchange) -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return rawPath;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
