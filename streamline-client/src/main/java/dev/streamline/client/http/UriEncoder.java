package dev.streamline.client.http;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * RFC 3986 percent encoding for individual URI components.
 */
public final class UriEncoder {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UriEncoder() {}

    /**
     * Encodes one UTF-8 path segment, preserving only RFC 3986 unreserved characters.
     *
     * @param segment the unencoded path segment
     * @return the percent-encoded segment
     * @throws NullPointerException if {@code segment} is {@code null}
     * @throws IllegalArgumentException if {@code segment} contains malformed Unicode
     */
    public static String encodePathSegment(String segment) {
        Objects.requireNonNull(segment, "segment must not be null");

        // Exact dot segments have traversal semantics during URI resolution.
        if (segment.equals(".")) {
            return "%2E";
        }
        if (segment.equals("..")) {
            return "%2E%2E";
        }

        ByteBuffer bytes;
        try {
            bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(segment));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("segment contains malformed Unicode", e);
        }

        StringBuilder encoded = new StringBuilder(bytes.remaining());
        while (bytes.hasRemaining()) {
            int value = bytes.get() & 0xff;
            if (isUnreserved(value)) {
                encoded.append((char) value);
            } else {
                encoded.append('%')
                        .append(HEX[value >>> 4])
                        .append(HEX[value & 0x0f]);
            }
        }
        return encoded.toString();
    }

    /**
     * Encodes a single query parameter value without form-encoding spaces as {@code +}.
     *
     * @param value the unencoded value
     * @return the percent-encoded value
     */
    public static String encodeQueryParameter(String value) {
        return encodePathSegment(value);
    }

    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }
}
