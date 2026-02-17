/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.P3FrameExtractor;
import com.dialtone.protocol.P3FrameMetadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for P3FrameExtractor Stream ID extraction.
 */
public class P3FrameExtractorTest {

    @Test
    @DisplayName("Should extract Stream ID 0x2100 from P3 frame header")
    public void testExtractStreamIdFromHeader() {
        // Create a test AOL v2/P3 frame with Stream ID 0x2100 at offset 10-11
        // AOL v2 frame format: magic(1), crc(2), len(2), tx(1), rx(1), type(1), token(2), streamId(2), payload...
        byte[] testFrame = new byte[] {
            0x5A,                   // Byte 0: Magic (0x5A)
            0x00, 0x00,             // Bytes 1-2: CRC (placeholder)
            0x00, 0x04,             // Bytes 3-4: Payload length (4 bytes)
            0x11,                   // Byte 5: TX sequence
            0x11,                   // Byte 6: RX sequence
            0x20,                   // Byte 7: Type (DATA)
            'f', 'h',               // Bytes 8-9: Token
            0x21, 0x00,             // Bytes 10-11: Stream ID (0x2100 = 8448 in big-endian)
            0x01, 0x02, 0x03, 0x04  // Bytes 12-15: Payload (4 bytes as declared)
        };

        P3FrameMetadata metadata = P3FrameExtractor.extractMetadata(testFrame);
        assertEquals(0x2100, metadata.getStreamId());
        assertEquals("fh", metadata.getToken());
    }

    @Test
    @DisplayName("Should handle different Stream ID values")
    public void testDifferentStreamIds() {
        // Test with Stream ID 0x0042
        byte[] frame1 = buildTestFrame("AT", 0x0042);
        P3FrameMetadata metadata1 = P3FrameExtractor.extractMetadata(frame1);
        assertEquals(0x0042, metadata1.getStreamId());

        // Test with Stream ID 0xFFEE
        byte[] frame2 = buildTestFrame("at", 0xFFEE);
        P3FrameMetadata metadata2 = P3FrameExtractor.extractMetadata(frame2);
        assertEquals(0xFFEE, metadata2.getStreamId());
    }

    @Test
    @DisplayName("Should build DOD response frame with correct Stream ID")
    public void testBuildDodResponseFrame() {
        int streamId = 0x2100;
        byte[] payload = new byte[] { 0x01, 0x02, 0x03 };
        String token = "AT";

        byte[] frame = P3FrameExtractor.buildDodResponseFrame(streamId, payload, token);

        // Verify frame structure
        assertEquals(0x5A, frame[0] & 0xFF); // Sync byte
        assertEquals(0x20, frame[1] & 0xFF); // DATA type
        assertEquals('A', frame[2]);         // Token first char
        assertEquals('T', frame[3]);         // Token second char

        // Verify Stream ID at offset 10-11
        assertEquals(0x21, frame[10] & 0xFF); // Stream ID high byte
        assertEquals(0x00, frame[11] & 0xFF); // Stream ID low byte

        // Verify payload is copied correctly
        assertEquals(payload[0], frame[12]);
        assertEquals(payload[1], frame[13]);
        assertEquals(payload[2], frame[14]);
    }

    @Test
    @DisplayName("Should dump frame header in readable format")
    public void testDumpFrameHeader() {
        // AOL v2 format: magic, crc(2), len(2), tx, rx, type, token(2), streamId(2), payload...
        byte[] frame = new byte[] {
            0x5A,                   // Magic
            0x00, 0x00,             // CRC
            0x00, 0x02,             // Length (2 bytes payload)
            0x11, 0x11,             // TX, RX
            0x20,                   // Type
            (byte)'f', (byte)'h',   // Token
            0x21, 0x00,             // Stream ID (0x2100)
            (byte) 0xFF, (byte) 0xEE // Payload
        };

        String dump = P3FrameExtractor.dumpFrameHeader(frame, 16);
        assertTrue(dump.contains("5A"));
        assertTrue(dump.contains("21 00")); // Stream ID
        assertTrue(dump.contains("|"));     // Header separator
    }

    /**
     * Helper to build test frames with specific parameters in AOL v2 format.
     * AOL v2 format: magic(1), crc(2), len(2), tx(1), rx(1), type(1), token(2), streamId(2), payload...
     */
    private byte[] buildTestFrame(String token, int streamId) {
        byte[] frame = new byte[20];
        frame[0] = 0x5A;                             // Byte 0: Magic
        frame[1] = 0x00;                             // Byte 1: CRC high (placeholder)
        frame[2] = 0x00;                             // Byte 2: CRC low (placeholder)
        frame[3] = 0x00;                             // Byte 3: Payload len high
        frame[4] = 0x08;                             // Byte 4: Payload len low (8 bytes)
        frame[5] = 0x11;                             // Byte 5: TX sequence
        frame[6] = 0x11;                             // Byte 6: RX sequence
        frame[7] = 0x20;                             // Byte 7: Type (DATA)
        frame[8] = (byte) token.charAt(0);           // Byte 8: Token char 1
        frame[9] = (byte) token.charAt(1);           // Byte 9: Token char 2
        frame[10] = (byte) ((streamId >> 8) & 0xFF); // Byte 10: Stream ID high
        frame[11] = (byte) (streamId & 0xFF);        // Byte 11: Stream ID low
        // Add 8-byte payload (as declared in length field)
        for (int i = 12; i < 20; i++) {
            frame[i] = (byte) i;
        }
        return frame;
    }
}