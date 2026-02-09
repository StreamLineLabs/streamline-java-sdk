package dev.streamline.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable collection of message headers.
 */
public final class Headers {

    private final Map<String, String> headers;

    private Headers(Map<String, String> headers) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * Creates headers from a map.
     */
    public static Headers of(Map<String, String> headers) {
        return new Headers(headers);
    }

    /**
     * Creates a builder for headers.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns empty headers.
     */
    public static Headers empty() {
        return new Headers(Collections.emptyMap());
    }

    /**
     * Returns the header value for the given key.
     */
    public String get(String key) {
        return headers.get(key);
    }

    /**
     * Returns all headers as a map.
     */
    public Map<String, String> toMap() {
        return headers;
    }

    /**
     * Returns whether headers are empty.
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    /**
     * Builder for Headers.
     */
    public static class Builder {
        private final Map<String, String> headers = new LinkedHashMap<>();

        /**
         * Adds a header.
         */
        public Builder add(String key, String value) {
            headers.put(key, value);
            return this;
        }

        /**
         * Builds the headers.
         */
        public Headers build() {
            return new Headers(headers);
        }
    }
}
