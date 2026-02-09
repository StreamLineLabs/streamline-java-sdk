package dev.streamline.client;

/**
 * Exception thrown by Streamline client operations.
 */
public class StreamlineException extends RuntimeException {

    private final String errorCode;
    private final String hint;

    public StreamlineException(String message) {
        this(message, null, null, null);
    }

    public StreamlineException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public StreamlineException(String message, String errorCode) {
        this(message, null, errorCode, null);
    }

    public StreamlineException(String message, Throwable cause, String errorCode, String hint) {
        super(message, cause);
        this.errorCode = errorCode;
        this.hint = hint;
    }

    /**
     * Returns the error code, if available.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns a hint for resolving the error, if available.
     */
    public String getHint() {
        return hint;
    }

    /**
     * Creates a topic not found exception.
     */
    public static StreamlineException topicNotFound(String topic) {
        return new StreamlineException(
            "Topic not found: " + topic,
            null,
            "TOPIC_NOT_FOUND",
            "Create the topic with: streamline-cli topics create " + topic
        );
    }

    /**
     * Creates a connection exception.
     */
    public static StreamlineException connectionFailed(String server, Throwable cause) {
        return new StreamlineException(
            "Failed to connect to " + server,
            cause,
            "CONNECTION_FAILED",
            "Check that Streamline server is running and accessible"
        );
    }

    /**
     * Creates a timeout exception.
     */
    public static StreamlineException timeout(String operation) {
        return new StreamlineException(
            "Operation timed out: " + operation,
            null,
            "TIMEOUT",
            "Consider increasing timeout settings or checking server load"
        );
    }
}
