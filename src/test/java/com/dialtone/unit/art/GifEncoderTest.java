/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.art;

import com.dialtone.art.*;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GifEncoder.
 * Validates complete AOL art payload structure (header + GIF + padding).
 */
class GifEncoderTest {

    /**
     * Test that encoded payload has correct structure: header + GIF (no padding by default).
     */
    @Test
    void payloadStructureShouldBeCorrect() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Payload should be: 40 (header) + GIF size (no padding by default)
        assertTrue(payload.length > 40, "Payload should be at least header + minimal GIF");

        // First 40 bytes are the AOL header
        assertEquals(40, AolArtHeader.getHeaderSize(),
                "Header should be 40 bytes");

        // Verify GIF magic number follows header
        assertEquals('G', (char) payload[40], "GIF should start with 'G'");
        assertEquals('I', (char) payload[41], "GIF should have 'I' as second char");
        assertEquals('F', (char) payload[42], "GIF should have 'F' as third char");
    }

    /**
     * Test that GIF is converted to GIF87a format.
     */
    @Test
    void shouldConvertToGif87a() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Check GIF version at bytes 40-45 (after 40-byte header)
        String gifHeader = new String(payload, 40, 6);
        assertEquals("GIF87a", gifHeader, "GIF should be version 87a");
    }

    /**
     * Test that by default, no padding is added after GIF data (matching working transmission).
     */
    @Test
    void shouldHaveNoPaddingByDefault() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Find GIF terminator (0x3B) - should be last byte of payload
        assertEquals(0x3B, payload[payload.length - 1] & 0xFF,
                "GIF terminator (0x3B) should be the last byte (no padding by default)");

        // Verify no padding bytes after terminator
        int gifTerminatorIndex = -1;
        for (int i = 40; i < payload.length; i++) {
            if (payload[i] == 0x3B) {
                gifTerminatorIndex = i;
                break;
            }
        }

        assertTrue(gifTerminatorIndex > 40,
                "GIF terminator (0x3B) should be found after header");

        // After terminator, there should be NO padding by default
        int remainingBytes = payload.length - gifTerminatorIndex - 1;
        assertEquals(0, remainingBytes,
                "Should have 0 bytes after GIF terminator (no padding by default)");
    }

    /**
     * Test that header Size B correctly equals GIF size (no padding by default).
     */
    @Test
    void headerSizeBShouldEqualGifSize() throws Exception {
        BufferedImage image = new BufferedImage(42, 42, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 42, 42);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Read Size B from header (bytes 22-23)
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int sizeB = buffer.getShort(22) & 0xFFFF;

        // Calculate actual GIF size (no padding by default)
        int actualGifSize = payload.length - 40; // Total - header

        assertEquals(actualGifSize, sizeB,
                "Size B in header should match actual GIF size (no padding by default)");
    }

    /**
     * Test that Size A relationship holds: Size_A = Size_B + 36.
     */
    @Test
    void headerSizeARelationshipShouldBeCorrect() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int sizeA = buffer.getShort(6) & 0xFFFF;   // Bytes 6-7
        int sizeB = buffer.getShort(22) & 0xFFFF;  // Bytes 22-23

        assertEquals(sizeB + 36, sizeA,
                "Size A should equal Size B + 36");
    }

    /**
     * Test that flag bytes from metadata are included in header.
     * NOTE: Flag byte 1 is enforced to 0x80 even if metadata specifies 0x00.
     */
    @Test
    void shouldUseMetadataFlagBytes() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32, 0x00, 0xE3);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Check flag bytes in header (bytes 36-37)
        // Flag byte 1 is enforced to 0x80 (AOL requirement), even if metadata specifies 0x00
        assertEquals(0x80, payload[36] & 0xFF,
                "Flag byte 1 should be 0x80 (enforced for AOL compatibility)");
        assertEquals(0xE3, payload[37] & 0xFF,
                "Flag byte 2 should match metadata");
    }

    /**
     * Test intelligent flag bytes when not specified in metadata.
     * All images now get:
     * - flagByte1 = 0x80 (Standard/Legacy, required by AOL)
     * - flagByte2 = 0xFD (DEFAULT, safest for all sizes)
     */
    @Test
    void shouldUseIntelligentFlagBytesWhenNotSpecified() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32); // No flag bytes specified

        byte[] payload = GifEncoder.encode(image, metadata);

        // Should intelligently calculate based on transparency and size
        assertEquals(0x80, payload[36] & 0xFF,
                "All images should use Standard/Legacy flag (0x80) as required by AOL");
        assertEquals(0xFD, payload[37] & 0xFF,
                "All images should use DEFAULT flag (0xFD) for safety");
    }

    /**
     * Test intelligent defaults for opaque 16×16 icon.
     * NOTE: All images now use 0x80 + 0xFD for maximum AOL compatibility.
     */
    @Test
    void shouldUseIntelligentDefaultsForTinyIcon() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(false, 16, 16); // Opaque, no flag bytes

        byte[] payload = GifEncoder.encode(image, metadata);

        // Should intelligently calculate
        assertEquals(0x80, payload[36] & 0xFF,
                "All images should use Standard/Legacy flag (0x80) as required by AOL");
        assertEquals(0xFD, payload[37] & 0xFF,
                "All images should use DEFAULT flag (0xFD) for safety");
    }

    /**
     * CRITICAL: Test that NO Graphic Control Extension (GCE) exists in GIF.
     * AOL expects pure GIF87a with NO GCE blocks. Transparency is handled via
     * palette index 0 convention, not via GCE.
     */
    @Test
    void shouldNotContainGraphicControlExtension() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Search for GCE pattern: 0x21 0xF9 (should NOT be found)
        boolean foundGCE = false;
        for (int i = 40; i < payload.length - 7; i++) {
            if ((payload[i] & 0xFF) == 0x21 && (payload[i + 1] & 0xFF) == 0xF9 &&
                (payload[i + 2] & 0xFF) == 0x04) {
                foundGCE = true;
                break;
            }
        }

        assertFalse(foundGCE,
                "GIF87a should NOT contain Graphic Control Extension (AOL expects pure GIF87a)");
    }

    /**
     * CRITICAL: Test that background color index is 0 in LSD.
     * AOL uses background index 0 as the transparency convention for GIF87a icons.
     */
    @Test
    void shouldHaveBackgroundColorIndexZero() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(false, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Background color index is at byte 51 (40-byte header + 6-byte "GIF87a" + 5 bytes into LSD)
        // LSD structure: width(2) + height(2) + packed(1) + bgIndex(1) + aspect(1) = 7 bytes
        // So bgIndex is at: 40 (header) + 6 (GIF87a) + 5 (width+height+packed) = byte 51
        int bgIndexOffset = 40 + 6 + 5;  // = 51
        int bgIndex = payload[bgIndexOffset] & 0xFF;

        assertEquals(0, bgIndex,
                String.format("Background color index should be 0 (AOL convention), got %d", bgIndex));
    }

    /**
     * Test that palette index 0 is reserved as transparent in the color model.
     * This is the AOL GIF87a transparency convention - no GCE required.
     */
    @Test
    void shouldReservePaletteIndexZeroAsTransparent() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // The color model itself has index 0 as transparent, but this is internal to Java
        // The GIF87a file just uses palette index 0, and AOL treats it as transparent by convention
        // We can verify the GIF is valid by checking it has proper structure
        assertTrue(payload.length > 100,
                "Payload should be properly sized with palette");

        // Verify no GCE (already tested elsewhere, but confirms transparency handling)
        boolean foundGCE = false;
        for (int i = 40; i < payload.length - 7; i++) {
            if ((payload[i] & 0xFF) == 0x21 && (payload[i + 1] & 0xFF) == 0xF9) {
                foundGCE = true;
                break;
            }
        }
        assertFalse(foundGCE, "Should use palette index 0 convention, not GCE");
    }

    /**
     * Test that image dimensions are correctly encoded in header.
     */
    @Test
    void shouldEncodeDimensionsInHeader() throws Exception {
        int width = 146;
        int height = 29;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, width, height);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Read dimensions from header (bytes 16-19)
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int encodedWidth = buffer.getShort(16) & 0xFFFF;
        int encodedHeight = buffer.getShort(18) & 0xFFFF;

        assertEquals(width, encodedWidth, "Width should be encoded in header");
        assertEquals(height, encodedHeight, "Height should be encoded in header");
    }

    /**
     * Test resize functionality.
     */
    @Test
    void shouldResizeImage() {
        BufferedImage original = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        BufferedImage resized = GifEncoder.resize(original, 42, 42);

        assertEquals(42, resized.getWidth(), "Resized width should match target");
        assertEquals(42, resized.getHeight(), "Resized height should match target");
    }

    /**
     * Test quantization to 256 colors.
     */
    @Test
    void shouldQuantizeTo256Colors() {
        BufferedImage original = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage quantized = GifEncoder.quantizeTo256Colors(original);

        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, quantized.getType(),
                "Quantized image should be indexed color");
        assertEquals(32, quantized.getWidth(), "Width should be preserved");
        assertEquals(32, quantized.getHeight(), "Height should be preserved");
    }

    /**
     * Test complete mathematical validation (matching working transmission).
     * Validates with 0 padding (default), matching user's proven working example.
     */
    @Test
    void completePayloadValidation() throws Exception {
        BufferedImage image = new BufferedImage(42, 42, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(false, 42, 42);

        byte[] payload = GifEncoder.encode(image, metadata);

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        // Extract all size fields
        int sizeA = buffer.getShort(6) & 0xFFFF;
        int sizeB = buffer.getShort(22) & 0xFFFF;
        int totalLength = payload.length;

        // Validate relationships (with 0 padding)
        assertEquals(totalLength - 40, sizeB,
                "Size B should equal GIF size (total - header, no padding)");
        assertEquals(sizeB + 36, sizeA,
                "Size A should equal Size B + 36");
        assertEquals(sizeA + 4, totalLength,
                "Total length should equal Size A + 4");

        // Verify GIF terminator is last byte (no padding)
        assertEquals(0x3B, payload[payload.length - 1] & 0xFF,
                "GIF terminator should be last byte");
    }

    /**
     * CRITICAL: Validate LSD has GCT enabled (packed byte = 0xF7).
     * AOL clients require Global Color Table in Logical Screen Descriptor.
     */
    @Test
    void shouldHaveGlobalColorTableInLSD() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // LSD starts at byte 46 (40-byte header + 6-byte "GIF87a")
        // LSD packed byte is at offset 10 from GIF start, which is byte 50 total
        int lsdPackedByteIndex = 40 + 10;  // 40-byte header + 10 bytes into GIF

        int lsdPackedByte = payload[lsdPackedByteIndex] & 0xFF;

        // Verify GCT is enabled (bit 7 = 1)
        assertTrue((lsdPackedByte & 0x80) != 0,
                String.format("LSD must have GCT enabled (bit 7 = 1), got packed byte: 0x%02X", lsdPackedByte));

        // Verify packed byte is exactly 0xF7 (standard for AOL)
        assertEquals(0xF7, lsdPackedByte,
                String.format("LSD packed byte should be 0xF7 for AOL compatibility, got: 0x%02X", lsdPackedByte));
    }

    /**
     * CRITICAL: Validate Image Descriptor has NO LCT (packed byte = 0x00).
     * AOL clients expect Global Color Table only, not Local Color Table.
     */
    @Test
    void shouldHaveNoLocalColorTableInImageDescriptor() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Find Image Descriptor (0x2C marker) after the 40-byte header
        int imageDescriptorIndex = -1;
        for (int i = 40; i < payload.length - 10; i++) {
            if ((payload[i] & 0xFF) == 0x2C) {
                imageDescriptorIndex = i;
                break;
            }
        }

        assertTrue(imageDescriptorIndex > 40,
                "Image Descriptor (0x2C) should be found after header");

        // Image Descriptor packed byte is at offset +9 from descriptor start
        int packedByteIndex = imageDescriptorIndex + 9;
        int packedByte = payload[packedByteIndex] & 0xFF;

        // Verify NO LCT (bit 7 = 0)
        assertFalse((packedByte & 0x80) != 0,
                String.format("Image Descriptor must NOT have LCT (bit 7 = 0), got packed byte: 0x%02X", packedByte));

        // Verify packed byte is exactly 0x00 (no LCT, no interlace, no sort)
        assertEquals(0x00, packedByte,
                String.format("Image Descriptor packed byte should be 0x00 for AOL compatibility, got: 0x%02X", packedByte));
    }

    /**
     * CRITICAL: Validate 768-byte Global Color Table exists after LSD.
     * AOL expects the palette to be a Global Color Table, not Local.
     */
    @Test
    void shouldHave768ByteGlobalColorTableAfterLSD() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(true, 32, 32);

        byte[] payload = GifEncoder.encode(image, metadata);

        // GCT should start at byte 53 (40-byte header + 6-byte "GIF87a" + 7-byte LSD)
        int gctStartIndex = 40 + 6 + 7;  // = 53

        // GCT is 768 bytes (256 colors × 3 bytes RGB)
        int gctEndIndex = gctStartIndex + 768;  // = 821

        assertTrue(payload.length > gctEndIndex,
                String.format("Payload must be large enough for GCT (need %d bytes, got %d)", gctEndIndex, payload.length));

        // Verify GCT is not all zeros (should have actual palette data)
        boolean hasNonZero = false;
        for (int i = gctStartIndex; i < gctEndIndex; i++) {
            if (payload[i] != 0) {
                hasNonZero = true;
                break;
            }
        }

        assertTrue(hasNonZero,
                "Global Color Table should contain non-zero palette data");
    }

    /**
     * CRITICAL: Validate header is exactly 40 bytes with all 4 flag bytes present.
     * This test ensures proper alignment and prevents the "only 3 bytes before GIF" bug.
     */
    @Test
    void shouldHaveExactly40ByteHeaderWithAllFlagBytes() throws Exception {
        BufferedImage image = new BufferedImage(42, 42, BufferedImage.TYPE_INT_ARGB);
        ArtMetadata metadata = new ArtMetadata(false, 42, 42);

        byte[] payload = GifEncoder.encode(image, metadata);

        // Verify header size
        assertTrue(payload.length >= 40, "Payload must be at least 40 bytes (header)");

        // Verify GIF starts at byte 40 (offset 0x28)
        assertEquals('G', (char) payload[40], "GIF must start at byte 40 with 'G'");
        assertEquals('I', (char) payload[41], "GIF byte 41 should be 'I'");
        assertEquals('F', (char) payload[42], "GIF byte 42 should be 'F'");

        // Verify Magic Marker B (bytes 26-35)
        assertEquals(0x24, payload[26] & 0xFF, "Byte 26 should be Magic Marker B start (0x24)");
        for (int i = 27; i <= 35; i++) {
            assertEquals(0x00, payload[i] & 0xFF,
                    String.format("Byte %d should be 0x00 (part of Magic Marker B)", i));
        }

        // CRITICAL: Verify all 4 flag bytes (36-39) are present before GIF
        int flagByte1 = payload[36] & 0xFF;
        int flagByte2 = payload[37] & 0xFF;
        int reserved1 = payload[38] & 0xFF;
        int reserved2 = payload[39] & 0xFF;

        // Flag bytes should be 80 FD 00 00 for opaque images with intelligent defaults
        assertEquals(0x80, flagByte1,
                "Flag byte 36 should be 0x80 (Standard/Legacy) as required by AOL");
        assertEquals(0xFD, flagByte2,
                "Flag byte 37 should be 0xFD (DEFAULT)");
        assertEquals(0x00, reserved1,
                "Flag byte 38 should be 0x00 (reserved)");
        assertEquals(0x00, reserved2,
                "Flag byte 39 should be 0x00 (reserved)");

        // Print hex dump for verification (first 45 bytes)
        StringBuilder hexDump = new StringBuilder();
        hexDump.append("\n=== Header Hex Dump (first 45 bytes) ===\n");
        for (int i = 0; i < Math.min(45, payload.length); i++) {
            if (i % 16 == 0) {
                hexDump.append(String.format("\n%04X: ", i));
            }
            hexDump.append(String.format("%02X ", payload[i] & 0xFF));
        }
        hexDump.append("\n");
        System.out.println(hexDump.toString());
    }

    /**
     * Test that resize preserves aspect ratio with portrait images.
     * Original: 1200×1648 (portrait)
     * Target: 256×256 (square)
     * Expected: 186×256 (height wins, width scaled proportionally)
     */
    @Test
    void shouldPreserveAspectRatioForPortraitImage() {
        BufferedImage original = new BufferedImage(1200, 1648, BufferedImage.TYPE_INT_ARGB);

        BufferedImage resized = GifEncoder.resize(original, 256, 256);

        // Height should hit its limit (256), width should scale proportionally
        assertEquals(186, resized.getWidth(),
                "Width should be scaled to 186 to preserve aspect ratio");
        assertEquals(256, resized.getHeight(),
                "Height should be at target limit");

        // Verify aspect ratio preserved (within rounding tolerance)
        double originalAspect = (double) original.getWidth() / original.getHeight();
        double resizedAspect = (double) resized.getWidth() / resized.getHeight();
        assertEquals(originalAspect, resizedAspect, 0.01,
                "Aspect ratio should be preserved (1200:1648 = 0.728)");
    }

    /**
     * Test that resize preserves aspect ratio with landscape images.
     * Original: 400×200 (landscape, 2:1)
     * Target: 256×256 (square)
     * Expected: 256×128 (width wins, height scaled proportionally)
     */
    @Test
    void shouldPreserveAspectRatioForLandscapeImage() {
        BufferedImage original = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);

        BufferedImage resized = GifEncoder.resize(original, 256, 256);

        // Width should hit its limit (256), height should scale proportionally
        assertEquals(256, resized.getWidth(),
                "Width should be at target limit");
        assertEquals(128, resized.getHeight(),
                "Height should be scaled to 128 to preserve aspect ratio");

        // Verify aspect ratio preserved
        double originalAspect = (double) original.getWidth() / original.getHeight();
        double resizedAspect = (double) resized.getWidth() / resized.getHeight();
        assertEquals(originalAspect, resizedAspect, 0.01,
                "Aspect ratio should be preserved (400:200 = 2.0)");
    }

    /**
     * Test that resize handles square images correctly.
     * Original: 300×300 (square)
     * Target: 256×256 (square)
     * Expected: 256×256 (both dimensions scale equally)
     */
    @Test
    void shouldHandleSquareImageResize() {
        BufferedImage original = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);

        BufferedImage resized = GifEncoder.resize(original, 256, 256);

        // Both dimensions should hit target
        assertEquals(256, resized.getWidth(),
                "Width should be at target limit");
        assertEquals(256, resized.getHeight(),
                "Height should be at target limit");
    }

    /**
     * Test that resize handles images already smaller than target.
     * Original: 100×100
     * Target: 256×256
     * Expected: 256×256 (upscaled proportionally)
     */
    @Test
    void shouldUpscaleSmallImages() {
        BufferedImage original = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage resized = GifEncoder.resize(original, 256, 256);

        // Should upscale to target
        assertEquals(256, resized.getWidth(),
                "Width should be upscaled to 256");
        assertEquals(256, resized.getHeight(),
                "Height should be upscaled to 256");
    }
}
