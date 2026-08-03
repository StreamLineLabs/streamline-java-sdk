package dev.streamline.testsupport;

/**
 * Endpoints used by unit tests.
 *
 * <p>Unit tests must be hermetic: they may construct real client objects, but they must
 * never reach a service that happens to be listening on the developer's machine — that
 * would make results depend on whether a local Streamline server is running. They
 * therefore point at TEST-NET-1 (RFC 5737), a range reserved for documentation and
 * guaranteed to carry no traffic, instead of {@code localhost}.
 *
 * <p>A literal IP is used on purpose: Kafka clients reject bootstrap addresses that do
 * not resolve, so a reserved DNS name such as {@code broker.invalid} cannot be used.
 */
public final class UnitTestEndpoints {

    /** Unroutable bootstrap servers for unit tests. */
    public static final String BOOTSTRAP_SERVERS = "192.0.2.1:9092";

    /** Unroutable HTTP API base URL for unit tests. */
    public static final String HTTP_URL = "http://192.0.2.1:9094";

    private UnitTestEndpoints() {
    }
}
