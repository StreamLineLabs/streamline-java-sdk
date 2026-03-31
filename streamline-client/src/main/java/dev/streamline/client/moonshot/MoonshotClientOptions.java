package dev.streamline.client.moonshot;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration options for moonshot HTTP clients.
 *
 * @param httpUrl    Broker HTTP base URL, e.g. {@code http://localhost:9094}.
 * @param timeout    Per-request timeout. Defaults to 30 seconds when null.
 * @param httpClient Optional pre-built {@link HttpClient}. When null, a default one is created.
 */
public record MoonshotClientOptions(String httpUrl, Duration timeout, HttpClient httpClient) {
    public MoonshotClientOptions(String httpUrl) {
        this(httpUrl, null, null);
    }
}
