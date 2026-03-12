package dev.streamline.client.schema;

/**
 * Wire format mode for schema-aware serialization.
 *
 * <p>Streamline supports two wire formats for schema-encoded messages:
 *
 * <ul>
 *   <li><b>STREAMLINE</b> — Streamline-native format:
 *       {@code [4 bytes: "SL\0\1"] [4 bytes: schema ID] [payload]}
 *   <li><b>CONFLUENT</b> — Confluent Schema Registry-compatible format:
 *       {@code [1 byte: 0x00] [4 bytes: schema ID] [payload]}
 * </ul>
 *
 * <p>Use {@code CONFLUENT} when interoperating with Kafka clients that use
 * the Confluent Schema Registry wire format. Use {@code STREAMLINE} (the
 * default) for Streamline-native deployments.
 */
public enum WireFormat {

    /**
     * Streamline-native wire format (8-byte header).
     * <pre>
     *   [0x53 0x4C 0x00 0x01]  4-byte magic ("SL\0\1")
     *   [4-byte big-endian]     schema ID
     *   [N bytes]               payload
     * </pre>
     */
    STREAMLINE(
        new byte[]{0x53, 0x4C, 0x00, 0x01},
        8
    ),

    /**
     * Confluent Schema Registry-compatible wire format (5-byte header).
     * <pre>
     *   [0x00]                  1-byte magic
     *   [4-byte big-endian]     schema ID
     *   [N bytes]               payload
     * </pre>
     */
    CONFLUENT(
        new byte[]{0x00},
        5
    );

    private final byte[] magic;
    private final int headerSize;

    WireFormat(byte[] magic, int headerSize) {
        this.magic = magic;
        this.headerSize = headerSize;
    }

    /** Returns the magic byte sequence for this format. */
    public byte[] magic() {
        return magic.clone();
    }

    /** Returns the total header size (magic + 4-byte schema ID). */
    public int headerSize() {
        return headerSize;
    }

    /**
     * Detects the wire format from the first byte of the data.
     *
     * @param data the wire-format bytes
     * @return the detected wire format
     * @throws IllegalArgumentException if the format cannot be determined
     */
    public static WireFormat detect(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot detect wire format from empty data");
        }
        if (data[0] == 0x00) {
            return CONFLUENT;
        }
        if (data.length >= 4 && data[0] == 0x53 && data[1] == 0x4C
                && data[2] == 0x00 && data[3] == 0x01) {
            return STREAMLINE;
        }
        throw new IllegalArgumentException(
            "Unknown wire format: first byte is 0x" + String.format("%02x", data[0])
        );
    }
}
