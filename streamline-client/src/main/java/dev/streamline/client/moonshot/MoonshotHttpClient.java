package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamline.client.StreamlineException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Shared HTTP plumbing for the moonshot HTTP clients (M1, M2, M4, M5).
 *
 * <p>Uses the built-in {@link java.net.http.HttpClient} — no extra deps.
 *
 * <p>Stability tier: <b>Experimental</b>.
 */
public abstract class MoonshotHttpClient {

    /** A non-2xx response from the broker. */
    public static class HttpException extends StreamlineException {
        private final int status;
        private final String body;

        public HttpException(String method, String path, int status, String body) {
            super(method + " " + path + " -> HTTP " + status + ": "
                    + (body == null ? "" : body.substring(0, Math.min(body.length(), 512))));
            this.status = status;
            this.body = body == null ? "" : body;
        }

        public int status() { return status; }
        public String body() { return body; }
    }

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected final URI baseUrl;
    protected final HttpClient client;
    protected final Duration timeout;

    protected MoonshotHttpClient(MoonshotClientOptions opts) {
        Objects.requireNonNull(opts, "opts");
        if (opts.httpUrl() == null || opts.httpUrl().isEmpty()) {
            throw new IllegalArgumentException("httpUrl is required");
        }
        String trimmed = opts.httpUrl().endsWith("/")
                ? opts.httpUrl().substring(0, opts.httpUrl().length() - 1)
                : opts.httpUrl();
        this.baseUrl = URI.create(trimmed);
        this.timeout = opts.timeout() == null ? Duration.ofSeconds(30) : opts.timeout();
        this.client = opts.httpClient() == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).build()
                : opts.httpClient();
    }

    /** Result of an HTTP call. */
    protected static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }

    protected Response request(String method, String path, Object jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json");
        try {
            HttpRequest.BodyPublisher publisher;
            if (jsonBody == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else {
                byte[] bytes = JSON.writeValueAsBytes(jsonBody);
                publisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
                b.header("Content-Type", "application/json");
            }
            b.method(method, publisher);
            HttpResponse<byte[]> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            String body = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);
            return new Response(resp.statusCode(), body);
        } catch (java.io.IOException e) {
            throw new StreamlineException("HTTP " + method + " " + path + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("HTTP " + method + " " + path + " interrupted", e);
        }
    }

    protected static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
