/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.art;

import com.dialtone.art.ArtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ArtService, including GIF pass-through functionality.
 */
class ArtServiceTest {

    private ArtService artService;

    @BeforeEach
    void setUp() {
        artService = new ArtService();
    }

    /**
     * Test that artExists() returns true for GIF+JSON assets (standard processing).
     */
    @Test
    void artExistsShouldReturnTrueForGifWithJson() {
        // 1-0-21003 has both .gif and .json in resources
        assertTrue(artService.artExists("1-0-21003"),
                "artExists should return true for GIF with JSON metadata");
    }

    /**
     * Test that artExists() returns true for GIF-only assets (pass-through mode).
     */
    @Test
    void artExistsShouldReturnTrueForGifOnly() {
        // 1-0-21004 has .gif but no .json (extracted from AOL database)
        assertTrue(artService.artExists("1-0-21004"),
                "artExists should return true for GIF without JSON (pass-through mode)");
    }

    /**
     * Test that artExists() returns false for PNG/JPG without JSON.
     * Only GIF files are allowed in pass-through mode.
     */
    @Test
    void artExistsShouldReturnFalseForPngWithoutJson() {
        // If a PNG exists without JSON, it should return false
        // (PNG requires processing, which needs metadata)
        assertFalse(artService.artExists("nonexistent-png-only"),
                "artExists should return false for PNG without JSON");
    }

    /**
     * Test that artExists() returns false for completely missing assets.
     */
    @Test
    void artExistsShouldReturnFalseForMissingAsset() {
        assertFalse(artService.artExists("completely-nonexistent-asset"),
                "artExists should return false for missing asset");
    }

    /**
     * Test GIF pass-through mode: GIF without JSON should be wrapped with AOL header
     * and returned without resize/quantize/dither processing.
     */
    @Test
    void shouldPassThroughGifWithoutJson() throws IOException {
        // 1-0-21004.gif exists without .json
        byte[] payload = artService.getArtAsBytes("1-0-21004");

        assertNotNull(payload, "Should return art bytes");
        assertTrue(payload.length > 40, "Payload should be at least header + minimal GIF");

        // Verify payload structure: 40-byte AOL header + GIF data
        // Bytes 40-45 should be "GIF87a" or "GIF89a"
        assertEquals('G', (char) payload[40], "GIF should start at byte 40 with 'G'");
        assertEquals('I', (char) payload[41], "GIF byte 41 should be 'I'");
        assertEquals('F', (char) payload[42], "GIF byte 42 should be 'F'");

        // Verify header has proper structure
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        // Check magic constants
        int magicConstantA = buffer.getShort(14) & 0xFFFF;
        assertEquals(0x0205, magicConstantA, "Magic constant A should be 0x0205");

        int magicMarkerB = payload[26] & 0xFF;
        assertEquals(0x24, magicMarkerB, "Magic marker B should be 0x24");
    }

    /**
     * Test that GIFs with JSON still use standard processing (resize, quantize, dither).
     */
    @Test
    void shouldProcessGifWithJson() throws IOException {
        // 1-0-21003 has both .gif and .json
        byte[] payload = artService.getArtAsBytes("1-0-21003");

        assertNotNull(payload, "Should return art bytes");
        assertTrue(payload.length > 40, "Payload should be at least header + minimal GIF");

        // Verify that data was processed (has AOL header wrapper + GIF data)
        assertEquals('G', (char) payload[40], "Processed GIF should start at byte 40 with 'G'");
        assertEquals('I', (char) payload[41], "GIF byte 41 should be 'I'");
        assertEquals('F', (char) payload[42], "GIF byte 42 should be 'F'");
    }

    /**
     * Test that IOException is thrown when asset not found (no default fallback).
     */
    @Test
    void shouldThrowIOExceptionWhenNotFound() {
        // Request non-existent asset, should throw IOException (no default fallback)
        assertThrows(IOException.class, () -> {
            artService.getArtAsBytes("99999-nonexistent");
        }, "Should throw IOException when art doesn't exist (no default fallback)");
    }

    /**
     * Test that pass-through GIF has correct dimensions in AOL header.
     */
    @Test
    void passthroughGifShouldHaveCorrectDimensionsInHeader() throws IOException {
        // 1-0-21004.gif is 32x32 pixels
        byte[] payload = artService.getArtAsBytes("1-0-21004");

        // Extract dimensions from AOL header (bytes 16-19)
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int headerWidth = buffer.getShort(16) & 0xFFFF;
        int headerHeight = buffer.getShort(18) & 0xFFFF;

        // The GIF is 32x32 (0x20 x 0x20)
        assertEquals(32, headerWidth, "Header width should match GIF dimensions");
        assertEquals(32, headerHeight, "Header height should match GIF dimensions");
    }

    /**
     * Test that pass-through mode uses default flag bytes (0x80, 0xFD).
     */
    @Test
    void passthroughGifShouldUseDefaultFlagBytes() throws IOException {
        byte[] payload = artService.getArtAsBytes("1-0-21004");

        // Check flag bytes in header (bytes 36-37)
        assertEquals(0x80, payload[36] & 0xFF, "Flag byte 1 should be 0x80 (default category)");
        assertEquals(0xFD, payload[37] & 0xFF, "Flag byte 2 should be 0xFD (default type code)");
    }

    /**
     * Test getArtDimensions() with JSON metadata.
     * Should return actual GIF dimensions after aspect-ratio-preserving resize, not JSON target dimensions.
     */
    @Test
    void shouldGetDimensionsFromJsonMetadata() throws IOException {
        // 1-0-21000 has .json with target 64x64
        // After aspect-ratio-preserving resize, the actual GIF is 64x61
        int[] dimensions = artService.getArtDimensions("1-0-21000");

        assertNotNull(dimensions, "Dimensions should not be null");
        assertEquals(2, dimensions.length, "Should return [width, height] array");
        assertEquals(64, dimensions[0], "Width should be 64 (actual GIF dimension)");
        assertEquals(61, dimensions[1], "Height should be 61 (actual GIF dimension after aspect-ratio resize)");
    }

    /**
     * Test getArtDimensions() with GIF-only asset (pass-through mode).
     */
    @Test
    void shouldGetDimensionsFromGifHeader() throws IOException {
        // 1-0-21004.gif is 32x32 (no JSON metadata)
        int[] dimensions = artService.getArtDimensions("1-0-21004");

        assertNotNull(dimensions, "Dimensions should not be null");
        assertEquals(2, dimensions.length, "Should return [width, height] array");
        assertEquals(32, dimensions[0], "Width should be 32 from GIF header");
        assertEquals(32, dimensions[1], "Height should be 32 from GIF header");
    }

    /**
     * Test getArtDimensions() with square image (aspect-ratio-preserving resize).
     * Uses image with JSON in root directory.
     */
    @Test
    void shouldGetDimensionsForLargerImage() throws IOException {
        // 1-0-21001 has .json with target 32x32
        // Source image is 1156x1157 (essentially square), so after aspect-ratio-preserving resize, the actual GIF is 32x32
        int[] dimensions = artService.getArtDimensions("1-0-21001");

        assertNotNull(dimensions, "Dimensions should not be null");
        assertEquals(2, dimensions.length, "Should return [width, height] array");
        assertEquals(32, dimensions[0], "Width should be 32 (actual GIF dimension after aspect-ratio resize)");
        assertEquals(32, dimensions[1], "Height should be 32 (actual GIF dimension)");
    }

    /**
     * Test getArtDimensions() throws IOException when art doesn't exist (no default fallback).
     */
    @Test
    void shouldThrowIOExceptionForDimensionsWhenNotFound() {
        // Request non-existent asset, should throw IOException (no default fallback)
        assertThrows(IOException.class, () -> {
            artService.getArtDimensions("99999-nonexistent");
        }, "Should throw IOException when art doesn't exist for dimension lookup (no default fallback)");
    }
}
