package dev.streamline.spring;

import dev.streamline.client.Headers;
import dev.streamline.client.RecordMetadata;
import dev.streamline.client.Streamline;
import dev.streamline.client.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Template for common Streamline operations in Spring applications.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Autowired
 * private StreamlineTemplate streamline;
 *
 * public void publishEvent(Event event) {
 *     streamline.send("events", event.getId(), event);
 * }
 * }</pre>
 */
public class StreamlineTemplate {

    private static final Logger log = LoggerFactory.getLogger(StreamlineTemplate.class);

    private final Streamline streamline;

    public StreamlineTemplate(Streamline streamline) {
        this.streamline = streamline;
    }

    /**
     * Sends a message to the specified topic.
     *
     * @param topic the topic name
     * @param key   the message key
     * @param value the message value
     * @return the record metadata
     */
    public RecordMetadata send(String topic, Object key, Object value) {
        return send(topic, key, value, null);
    }

    /**
     * Sends a message to the specified topic with headers.
     *
     * @param topic   the topic name
     * @param key     the message key
     * @param value   the message value
     * @param headers the message headers
     * @return the record metadata
     */
    public RecordMetadata send(String topic, Object key, Object value, Headers headers) {
        String keyStr = key != null ? key.toString() : null;
        String valueStr = serializeValue(value);
        return streamline.produce(topic, keyStr, valueStr, headers);
    }

    /**
     * Sends a message asynchronously.
     *
     * @param topic the topic name
     * @param key   the message key
     * @param value the message value
     * @return a future that completes with the record metadata
     */
    public CompletableFuture<RecordMetadata> sendAsync(String topic, Object key, Object value) {
        String keyStr = key != null ? key.toString() : null;
        String valueStr = serializeValue(value);
        return streamline.produceAsync(topic, keyStr, valueStr);
    }

    /**
     * Returns the underlying Streamline client.
     *
     * @return the client
     */
    public Streamline getStreamline() {
        return streamline;
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // TODO: Use Jackson for serialization
        return value.toString();
    }
}
