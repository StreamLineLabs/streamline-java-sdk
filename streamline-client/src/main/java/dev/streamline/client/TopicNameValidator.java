package dev.streamline.client;

import java.util.regex.Pattern;

/**
 * Validates Kafka topic names according to the Kafka protocol specification.
 *
 * <p>Rules:
 * <ul>
 *   <li>Must not be null or empty</li>
 *   <li>Maximum 249 characters</li>
 *   <li>Only alphanumeric characters, '.', '_', and '-' are allowed</li>
 *   <li>Must not be "." or ".."</li>
 * </ul>
 */
public final class TopicNameValidator {

    static final int MAX_TOPIC_NAME_LENGTH = 249;
    private static final Pattern VALID_CHARS = Pattern.compile("[a-zA-Z0-9._-]+");

    private TopicNameValidator() {}

    /**
     * Validates a topic name and throws {@link IllegalArgumentException} if invalid.
     *
     * @param topic the topic name to validate
     * @throws IllegalArgumentException if the topic name is invalid
     */
    public static void validate(String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Topic name must not be null or empty");
        }
        if (topic.length() > MAX_TOPIC_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Topic name must not exceed " + MAX_TOPIC_NAME_LENGTH + " characters, got " + topic.length());
        }
        if (".".equals(topic) || "..".equals(topic)) {
            throw new IllegalArgumentException(
                "Topic name must not be \".\" or \"..\"");
        }
        if (!VALID_CHARS.matcher(topic).matches()) {
            throw new IllegalArgumentException(
                "Topic name contains invalid characters. "
                    + "Only alphanumeric characters, '.', '_', and '-' are allowed: " + topic);
        }
    }
}
