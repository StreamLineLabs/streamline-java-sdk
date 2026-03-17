package dev.streamline.client.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WireFormatTest {

    @Test
    void testStreamlineMagicBytes() {
        byte[] magic = WireFormat.STREAMLINE.magic();
        assertEquals(4, magic.length);
        assertEquals(0x53, magic[0]); // 'S'
        assertEquals(0x4C, magic[1]); // 'L'
        assertEquals(0x00, magic[2]);
        assertEquals(0x01, magic[3]);
    }

    @Test
    void testConfluentMagicByte() {
        byte[] magic = WireFormat.CONFLUENT.magic();
        assertEquals(1, magic.length);
        assertEquals(0x00, magic[0]);
    }

    @Test
    void testStreamlineHeaderSize() {
        assertEquals(8, WireFormat.STREAMLINE.headerSize());
    }

    @Test
    void testConfluentHeaderSize() {
        assertEquals(5, WireFormat.CONFLUENT.headerSize());
    }

    @Test
    void testMagicReturnsDefensiveCopy() {
        byte[] magic1 = WireFormat.STREAMLINE.magic();
        byte[] magic2 = WireFormat.STREAMLINE.magic();
        assertNotSame(magic1, magic2);
        // Mutating one should not affect the other
        magic1[0] = 0;
        assertNotEquals(magic1[0], magic2[0]);
    }

    @Test
    void testDetectConfluent() {
        byte[] data = new byte[]{0x00, 0x00, 0x00, 0x00, 0x2A}; // magic + schema ID 42
        assertEquals(WireFormat.CONFLUENT, WireFormat.detect(data));
    }

    @Test
    void testDetectStreamline() {
        byte[] data = new byte[]{0x53, 0x4C, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01};
        assertEquals(WireFormat.STREAMLINE, WireFormat.detect(data));
    }

    @Test
    void testDetectNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(null));
    }

    @Test
    void testDetectEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(new byte[]{}));
    }

    @Test
    void testDetectUnknownMagicThrows() {
        byte[] data = new byte[]{(byte) 0xFF, 0x01, 0x02};
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> WireFormat.detect(data)
        );
        assertTrue(ex.getMessage().contains("Unknown wire format"));
        assertTrue(ex.getMessage().contains("0xff"));
    }

    @Test
    void testDetectSingleByte0x00IsConfluent() {
        byte[] data = new byte[]{0x00};
        assertEquals(WireFormat.CONFLUENT, WireFormat.detect(data));
    }

    @Test
    void testDetectPartialStreamlineMagicFallsThrough() {
        // Only 3 bytes of Streamline magic — not enough to match
        byte[] data = new byte[]{0x53, 0x4C, 0x00};
        assertThrows(IllegalArgumentException.class, () -> WireFormat.detect(data));
    }

    @Test
    void testEnumValues() {
        WireFormat[] values = WireFormat.values();
        assertEquals(2, values.length);
        assertEquals(WireFormat.STREAMLINE, WireFormat.valueOf("STREAMLINE"));
        assertEquals(WireFormat.CONFLUENT, WireFormat.valueOf("CONFLUENT"));
    }
}
