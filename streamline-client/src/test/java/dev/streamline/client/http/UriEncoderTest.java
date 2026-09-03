package dev.streamline.client.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriEncoderTest {

    @Test
    void preservesOnlyUnreservedAsciiCharacters() {
        assertEquals("AZaz09-._~", UriEncoder.encodePathSegment("AZaz09-._~"));
    }

    @Test
    void encodesReservedWhitespacePercentUnicodeAndTraversalLikeInput() {
        String encoded = UriEncoder.encodePathSegment("../a/b c%?#雪😀");

        assertEquals("..%2Fa%2Fb%20c%25%3F%23%E9%9B%AA%F0%9F%98%80", encoded);
        assertFalse(encoded.contains("+"));
    }

    @Test
    void encodesExactDotSegmentsToPreventUriNormalization() {
        assertEquals("%2E", UriEncoder.encodePathSegment("."));
        assertEquals("%2E%2E", UriEncoder.encodePathSegment(".."));
    }

    @Test
    void rejectsNullAndMalformedUnicode() {
        assertThrows(NullPointerException.class, () -> UriEncoder.encodePathSegment(null));
        assertThrows(IllegalArgumentException.class,
                () -> UriEncoder.encodePathSegment(String.valueOf((char) 0xd800)));
    }
}
