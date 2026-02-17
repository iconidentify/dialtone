/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.xfer;

import com.dialtone.protocol.xfer.XferEncoder;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XFER escape encoding.
 */
class XferEncoderTest {

    @Test
    void shouldIdentifyBytesNeedingEscape() {
        // These bytes MUST be escaped
        assertTrue(XferEncoder.needsEscape(XferEncoder.DL_ESC));      // 0x5D
        assertTrue(XferEncoder.needsEscape(XferEncoder.DL_RUN));      // 0x5B
        assertTrue(XferEncoder.needsEscape((byte) 0x0D));             // CR
        assertTrue(XferEncoder.needsEscape((byte) 0x8D));             // CR high-bit

        // Normal bytes should NOT be escaped
        assertFalse(XferEncoder.needsEscape((byte) 0x00));
        assertFalse(XferEncoder.needsEscape((byte) 0x41));  // 'A'
        assertFalse(XferEncoder.needsEscape((byte) 0x0A));  // LF
        assertFalse(XferEncoder.needsEscape((byte) 0xFF));
    }

    @Test
    void shouldNotModifyNormalBytes() {
        byte[] input = "tiny\n".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = XferEncoder.encode(input);

        // "tiny\n" has no special bytes, should pass through unchanged
        assertArrayEquals(input, encoded);
    }

    @Test
    void shouldEscapeSpecialBytes() {
        // Test escaping DL_ESC (0x5D)
        byte[] input1 = new byte[] { XferEncoder.DL_ESC };
        byte[] encoded1 = XferEncoder.encode(input1);
        assertEquals(2, encoded1.length);
        assertEquals(XferEncoder.DL_ESC, encoded1[0]);
        assertEquals((byte) (XferEncoder.DL_ESC ^ XferEncoder.DL_XOR), encoded1[1]);

        // Test escaping DL_RUN (0x5B)
        byte[] input2 = new byte[] { XferEncoder.DL_RUN };
        byte[] encoded2 = XferEncoder.encode(input2);
        assertEquals(2, encoded2.length);
        assertEquals(XferEncoder.DL_ESC, encoded2[0]);
        assertEquals((byte) (XferEncoder.DL_RUN ^ XferEncoder.DL_XOR), encoded2[1]);

        // Test escaping CR (0x0D)
        byte[] input3 = new byte[] { 0x0D };
        byte[] encoded3 = XferEncoder.encode(input3);
        assertEquals(2, encoded3.length);
        assertEquals(XferEncoder.DL_ESC, encoded3[0]);
        assertEquals((byte) (0x0D ^ XferEncoder.DL_XOR), encoded3[1]);

        // Test escaping CR_HIGH (0x8D)
        byte[] input4 = new byte[] { (byte) 0x8D };
        byte[] encoded4 = XferEncoder.encode(input4);
        assertEquals(2, encoded4.length);
        assertEquals(XferEncoder.DL_ESC, encoded4[0]);
        assertEquals((byte) (0x8D ^ XferEncoder.DL_XOR), encoded4[1]);
    }

    @Test
    void shouldHandleMixedContent() {
        // Mix of normal and special bytes
        byte[] input = new byte[] { 'A', XferEncoder.DL_ESC, 'B', 0x0D, 'C' };
        byte[] encoded = XferEncoder.encode(input);

        // Expected: 'A', ESC, (0x5D^0x55), 'B', ESC, (0x0D^0x55), 'C'
        assertEquals(7, encoded.length);
        assertEquals('A', encoded[0]);
        assertEquals(XferEncoder.DL_ESC, encoded[1]);
        assertEquals((byte) (XferEncoder.DL_ESC ^ XferEncoder.DL_XOR), encoded[2]);
        assertEquals('B', encoded[3]);
        assertEquals(XferEncoder.DL_ESC, encoded[4]);
        assertEquals((byte) (0x0D ^ XferEncoder.DL_XOR), encoded[5]);
        assertEquals('C', encoded[6]);
    }

    @Test
    void shouldDecodeEscapedBytes() {
        // Encode then decode should produce original
        byte[] original = new byte[] { 'A', XferEncoder.DL_ESC, 'B', 0x0D, 'C' };
        byte[] encoded = XferEncoder.encode(original);
        byte[] decoded = XferEncoder.decode(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    void shouldRoundTripTinyFile() {
        // The actual MVP test file
        byte[] tinyFile = "tiny\n".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = XferEncoder.encode(tinyFile);
        byte[] decoded = XferEncoder.decode(encoded);

        assertArrayEquals(tinyFile, decoded);
        assertEquals("tiny\n", new String(decoded, StandardCharsets.US_ASCII));
    }

    @Test
    void shouldHandleEmptyInput() {
        assertArrayEquals(new byte[0], XferEncoder.encode(new byte[0]));
        assertArrayEquals(new byte[0], XferEncoder.encode(null));
        assertArrayEquals(new byte[0], XferEncoder.decode(new byte[0]));
        assertArrayEquals(new byte[0], XferEncoder.decode(null));
    }

    @Test
    void shouldHandleAllSpecialBytesConsecutive() {
        byte[] input = new byte[] {
            XferEncoder.DL_ESC,
            XferEncoder.DL_RUN,
            0x0D,
            (byte) 0x8D
        };
        byte[] encoded = XferEncoder.encode(input);
        byte[] decoded = XferEncoder.decode(encoded);

        assertArrayEquals(input, decoded);
        // Each special byte doubles in size
        assertEquals(8, encoded.length);
    }

    @Property
    void encodingRoundTripShouldPreserveData(@ForAll byte[] data) {
        byte[] encoded = XferEncoder.encode(data);
        byte[] decoded = XferEncoder.decode(encoded);
        assertArrayEquals(data, decoded);
    }

    @Property
    void encodedDataShouldNeverContainUnescapedSpecialBytes(@ForAll byte[] data) {
        byte[] encoded = XferEncoder.encode(data);

        // Walk through encoded data - special bytes should only appear after DL_ESC
        boolean escapeActive = false;
        for (int i = 0; i < encoded.length; i++) {
            byte b = encoded[i];
            if (escapeActive) {
                // This byte is escaped, skip check
                escapeActive = false;
            } else if (b == XferEncoder.DL_ESC) {
                escapeActive = true;
            } else {
                // This is a raw byte - must NOT be a special byte
                assertFalse(XferEncoder.needsEscape(b),
                    "Found unescaped special byte 0x" + String.format("%02X", b & 0xFF) +
                    " at position " + i);
            }
        }
    }
}
