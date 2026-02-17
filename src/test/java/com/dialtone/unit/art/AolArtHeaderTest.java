/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.art;

import com.dialtone.art.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AolArtHeader generation and validation.
 * Validates compliance with AOL 3.0 art format specification.
 */
class AolArtHeaderTest {

    /**
     * Test that header is exactly 40 bytes.
     */
    @Test
    void headerSizeShouldBe40Bytes() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);
        assertEquals(40, header.length, "Header must be exactly 40 bytes");
    }

    /**
     * Test magic constant A (bytes 14-15) is always 0x0205.
     */
    @Test
    void magicConstantAShouldBe0x0205() {
        byte[] header = AolArtHeader.generate(42, 42, 921, 1574, 0x80, 0xFD);

        // Read as little-endian short
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(14);
        short magicConstant = buffer.getShort();

        assertEquals(0x0205, magicConstant & 0xFFFF,
                "Magic constant A at bytes 14-15 must be 0x0205");
    }

    /**
     * Test magic marker B (bytes 26-35) is 0x24 followed by 9 null bytes.
     */
    @Test
    void magicMarkerBShouldBeCorrect() {
        byte[] header = AolArtHeader.generate(42, 42, 921, 1574, 0x80, 0xFD);

        // Byte 26 should be 0x24
        assertEquals(0x24, header[26] & 0xFF,
                "Magic marker B should start with 0x24 at byte 26");

        // Bytes 27-35 should all be 0x00
        for (int i = 27; i <= 35; i++) {
            assertEquals(0x00, header[i] & 0xFF,
                    "Magic marker B should have null bytes at position " + i);
        }
    }

    /**
     * Test version marker (bytes 0-3) is 0x00010001 (two shorts of 0x0001 in little-endian).
     */
    @Test
    void versionMarkerShouldBeCorrect() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);

        // Verify as two separate shorts for clarity
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        short version1 = buffer.getShort(0);
        short version2 = buffer.getShort(2);

        assertEquals(0x0001, version1 & 0xFFFF,
                "Version marker part 1 (bytes 0-1) should be 0x0001");
        assertEquals(0x0001, version2 & 0xFFFF,
                "Version marker part 2 (bytes 2-3) should be 0x0001");
    }

    /**
     * Test flag field (bytes 4-5) is 0x0001.
     */
    @Test
    void flagFieldShouldBeCorrect() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        short flagField = buffer.getShort(4);

        assertEquals(0x0001, flagField & 0xFFFF,
                "Flag field (bytes 4-5) should be 0x0001");
    }

    /**
     * Test mathematical relationship: Size_A = Size_B + 36
     */
    @ParameterizedTest
    @CsvSource({
        "500, 1000",    // GIF=500, padding=1000
        "921, 1574",    // Example from user's research
        "200, 94",      // Minimum padding
        "1000, 1574"    // Large padding
    })
    void sizeARelationship(int gifSize, int paddingSize) {
        byte[] header = AolArtHeader.generate(32, 32, gifSize, paddingSize, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int sizeA = buffer.getShort(6) & 0xFFFF;  // Bytes 6-7
        int sizeB = buffer.getShort(22) & 0xFFFF; // Bytes 22-23

        assertEquals(sizeB + 36, sizeA,
                "Size A (bytes 6-7) must equal Size B + 36");
    }

    /**
     * Test mathematical relationship: Size_B = GIF_size + Padding_size
     */
    @ParameterizedTest
    @CsvSource({
        "500, 1000",
        "921, 1574",
        "200, 94"
    })
    void sizeBRelationship(int gifSize, int paddingSize) {
        byte[] header = AolArtHeader.generate(32, 32, gifSize, paddingSize, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int sizeB = buffer.getShort(22) & 0xFFFF; // Bytes 22-23

        assertEquals(gifSize + paddingSize, sizeB,
                "Size B (bytes 22-23) must equal GIF size + padding size");
    }

    /**
     * Test complete mathematical chain: R2D2_Length = Size_A + 4
     */
    @Test
    void completeSizeChainValidation() {
        int gifSize = 921;
        int paddingSize = 1574;
        byte[] header = AolArtHeader.generate(42, 42, gifSize, paddingSize, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int sizeA = buffer.getShort(6) & 0xFFFF;   // Bytes 6-7
        int sizeB = buffer.getShort(22) & 0xFFFF;  // Bytes 22-23

        // Size B = GIF + padding
        assertEquals(gifSize + paddingSize, sizeB,
                "Size B must equal GIF size + padding size");

        // Size A = Size B + 36
        assertEquals(sizeB + 36, sizeA,
                "Size A must equal Size B + 36");

        // Total payload length = Size A + 4
        int expectedR2D2Length = sizeA + 4;
        assertEquals(2535, expectedR2D2Length,
                "Complete payload length matches user's research example");
    }

    /**
     * Test width and height encoding (little-endian at bytes 16-19).
     */
    @ParameterizedTest
    @CsvSource({
        "32, 32",
        "42, 42",
        "146, 29",
        "44, 33"
    })
    void widthAndHeightEncoding(int width, int height) {
        byte[] header = AolArtHeader.generate(width, height, 500, 1000, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int encodedWidth = buffer.getShort(16) & 0xFFFF;   // Bytes 16-17
        int encodedHeight = buffer.getShort(18) & 0xFFFF;  // Bytes 18-19

        assertEquals(width, encodedWidth, "Width must be correctly encoded at bytes 16-17");
        assertEquals(height, encodedHeight, "Height must be correctly encoded at bytes 18-19");
    }

    /**
     * Test all 6 flag byte patterns discovered in user's research.
     */
    @ParameterizedTest
    @CsvSource({
        "0x80, 0xFD",  // Most common (55%)
        "0x80, 0x00",  // 13%
        "0x00, 0xE3",  // 13%
        "0x80, 0x1E",  // 11%
        "0x00, 0x0F",  // 4%
        "0x80, 0x2A"   // 4%
    })
    void flagBytePatterns(String flag1Hex, String flag2Hex) {
        int flagByte1 = Integer.decode(flag1Hex);
        int flagByte2 = Integer.decode(flag2Hex);

        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, flagByte1, flagByte2);

        assertEquals(flagByte1, header[36] & 0xFF,
                "Flag byte 1 (byte 36) must match requested value");
        assertEquals(flagByte2, header[37] & 0xFF,
                "Flag byte 2 (byte 37) must match requested value");
    }

    /**
     * Test reserved bytes (38-39) are always 0x0000.
     */
    @Test
    void reservedBytesShouldBeZero() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);

        assertEquals(0x00, header[38] & 0xFF, "Byte 38 must be 0x00");
        assertEquals(0x00, header[39] & 0xFF, "Byte 39 must be 0x00");
    }

    /**
     * Test variable flags (bytes 8-11) contain expected pattern.
     */
    @Test
    void variableFlagsShouldBeCorrect() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);

        // Pattern: 00 00 01 00
        assertEquals(0x00, header[8] & 0xFF, "Byte 8 should be 0x00");
        assertEquals(0x00, header[9] & 0xFF, "Byte 9 should be 0x00");
        assertEquals(0x01, header[10] & 0xFF, "Byte 10 should be 0x01");
        assertEquals(0x00, header[11] & 0xFF, "Byte 11 should be 0x00");
    }

    /**
     * Test backward compatibility with deprecated 3-parameter method.
     */
    @Test
    @SuppressWarnings("deprecation")
    void backwardCompatibilityWithDeprecatedMethod() {
        byte[] header = AolArtHeader.generate(32, 32, 500);

        assertEquals(40, header.length, "Deprecated method should still produce 40-byte header");

        // Should use default padding = 0 and default flags = 0x80FD
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int sizeB = buffer.getShort(22) & 0xFFFF;

        assertEquals(500, sizeB, "With no padding, Size B should equal GIF size");
        assertEquals(0x80, header[36] & 0xFF, "Should use default flag byte 1 = 0x80");
        assertEquals(0xFD, header[37] & 0xFF, "Should use default flag byte 2 = 0xFD");
    }

    /**
     * Test 4-parameter method uses default flag bytes.
     */
    @Test
    void fourParameterMethodUsesDefaultFlags() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000);

        assertEquals(0x80, header[36] & 0xFF, "Should use default flag byte 1 = 0x80");
        assertEquals(0xFD, header[37] & 0xFF, "Should use default flag byte 2 = 0xFD");
    }

    /**
     * Test null padding region (bytes 20-21, 24-25).
     */
    @Test
    void nullPaddingRegionsShouldBeZero() {
        byte[] header = AolArtHeader.generate(32, 32, 500, 1000, 0x80, 0xFD);

        // Bytes 20-21: Zero padding
        assertEquals(0x00, header[20] & 0xFF, "Byte 20 should be 0x00");
        assertEquals(0x00, header[21] & 0xFF, "Byte 21 should be 0x00");

        // Bytes 24-25: Null padding
        assertEquals(0x00, header[24] & 0xFF, "Byte 24 should be 0x00");
        assertEquals(0x00, header[25] & 0xFF, "Byte 25 should be 0x00");
    }

    /**
     * Test with zero padding (matching working transmission).
     * Validates that mathematical relationships hold when padding = 0.
     */
    @Test
    void shouldWorkWithZeroPadding() {
        int gifSize = 1883;  // From user's working transmission
        int paddingSize = 0;  // No padding!
        byte[] header = AolArtHeader.generate(42, 42, gifSize, paddingSize, 0x80, 0xFD);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int sizeA = buffer.getShort(6) & 0xFFFF;   // Bytes 6-7
        int sizeB = buffer.getShort(22) & 0xFFFF;  // Bytes 22-23

        // Size B = GIF + 0 padding = 1883
        assertEquals(1883, sizeB,
                "Size B must equal GIF size when padding is 0");

        // Size A = Size B + 36 = 1919
        assertEquals(1919, sizeA,
                "Size A must equal 1919 (1883 + 36)");

        // Validate relationship
        assertEquals(sizeB + 36, sizeA,
                "Size A must equal Size B + 36");

        // Total payload would be Size A + 4 = 1923
        int totalExpected = sizeA + 4;
        assertEquals(1923, totalExpected,
                "Total payload should be 1923 bytes (matching working transmission)");
    }
}
