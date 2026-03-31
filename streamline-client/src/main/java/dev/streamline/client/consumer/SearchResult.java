package dev.streamline.client.consumer;

/**
 * A single search result from a topic.
 *
 * @param partition partition of the matching record
 * @param offset    offset of the matching record
 * @param score     similarity score (higher = more relevant)
 * @param value     record value, if returned by the server
 */
public record SearchResult(
    int partition,
    long offset,
    double score,
    String value
) {}
