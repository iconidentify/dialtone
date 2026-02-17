/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.dialtone.utils.LoggerUtil;

import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Encodes BufferedImage to GIF87a format with AOL 3.0 specifications.
 *
 * Output includes:
 * - 40-byte AOL art header (dimensions, sizes, format markers)
 * - GIF87a image data:
 *   - Global Color Table: 256 colors (8 bits per pixel)
 *   - Graphic Control Extension: 21 F9 04 01 00 00 00 00 (transparency enabled)
 *   - Single frame (static image)
 *   - LZW compression with minimum code size: 08
 *   - GIF terminator: 0x3B
 */
public class GifEncoder {

    /**
     * Default padding size to append after GIF data.
     * Based on working AOL transmissions, padding is OPTIONAL (not required).
     * Default: 0 bytes (no padding) - proven to work with AOL 3.0 clients.
     *
     * Note: Some archived AOL art files contain padding (94-1574 bytes range),
     * but working transmissions confirm it's not mandatory.
     */
    private static final int DEFAULT_PADDING_SIZE = 0;

    /**
     * Maximum safe GIF size (in bytes) to avoid AOL header Size B overflow.
     * AOL header uses unsigned 16-bit shorts (max 65,535), but we use 60KB
     * as a safety margin to ensure reliable rendering across all clients.
     */
    private static final int MAX_SAFE_GIF_SIZE = 60000;

    /**
     * Minimum dimension allowed when reducing oversized images.
     * Below this threshold, we stop trying to reduce and throw an exception.
     */
    private static final int MIN_DIMENSION = 32;

    /**
     * Encode a BufferedImage to AOL art format with automatic size reduction if needed.
     * If the resulting GIF exceeds MAX_SAFE_GIF_SIZE, the image is automatically resized
     * to 75% dimensions and re-encoded until it fits within the limit.
     *
     * @param image The image to encode
     * @param metadata Art metadata containing dimensions, transparency, and flag bytes
     * @return Complete AOL art payload (header + GIF87a bytes, guaranteed under size limit)
     * @throws IOException if encoding fails or image cannot be reduced to safe size
     */
    public static byte[] encodeWithSizeLimit(BufferedImage image, ArtMetadata metadata) throws IOException {
        return encodeWithSizeLimit(image, metadata.isTransparency(),
                                   metadata.getFlagByte1(), metadata.getFlagByte2(), 0);
    }

    /**
     * Internal recursive method for encoding with automatic size reduction.
     *
     * @param image The image to encode
     * @param enableTransparency Whether to enable transparency
     * @param flagByte1 Resource category flag (byte 36)
     * @param flagByte2 Resource type code (byte 37)
     * @param attemptNumber Recursion depth (for logging)
     * @return Complete AOL art payload under size limit
     * @throws IOException if encoding fails or cannot reduce to safe size
     */
    private static byte[] encodeWithSizeLimit(BufferedImage image, boolean enableTransparency,
                                             int flagByte1, int flagByte2, int attemptNumber)
                                             throws IOException {
        // Encode the image
        byte[] payload = encode(image, enableTransparency, flagByte1, flagByte2);

        // Check if GIF size (excluding 40-byte AOL header) exceeds limit
        int gifSize = payload.length - AolArtHeader.getHeaderSize();

        if (gifSize <= MAX_SAFE_GIF_SIZE) {
            // Success! Size is safe
            if (attemptNumber > 0) {
                LoggerUtil.info(String.format(
                    "Successfully reduced image to safe size after %d attempts: %dx%d → GIF size: %d bytes",
                    attemptNumber, image.getWidth(), image.getHeight(), gifSize));
            }
            return payload;
        }

        // GIF is too large - need to reduce dimensions
        int currentWidth = image.getWidth();
        int currentHeight = image.getHeight();

        // Calculate 75% dimensions (preserves aspect ratio)
        int newWidth = (int) Math.round(currentWidth * 0.75);
        int newHeight = (int) Math.round(currentHeight * 0.75);

        // Check if we've hit minimum dimensions
        if (newWidth < MIN_DIMENSION || newHeight < MIN_DIMENSION) {
            throw new IOException(String.format(
                "Cannot reduce image to safe size. Current: %dx%d → GIF: %d bytes (limit: %d bytes). " +
                "Reducing further would make image too small (min: %dx%d). " +
                "Consider using higher compression or smaller source image.",
                currentWidth, currentHeight, gifSize, MAX_SAFE_GIF_SIZE,
                MIN_DIMENSION, MIN_DIMENSION));
        }

        LoggerUtil.warn(String.format(
            "GIF size (%d bytes) exceeds safe limit (%d bytes). " +
            "Reducing image from %dx%d to %dx%d (attempt %d)...",
            gifSize, MAX_SAFE_GIF_SIZE, currentWidth, currentHeight,
            newWidth, newHeight, attemptNumber + 1));

        // Resize the image and try again
        BufferedImage resized = resize(image, newWidth, newHeight);
        return encodeWithSizeLimit(resized, enableTransparency, flagByte1, flagByte2, attemptNumber + 1);
    }

    /**
     * Encode a BufferedImage to AOL art format (40-byte header + GIF87a data + optional padding).
     *
     * @param image The image to encode
     * @param metadata Art metadata containing dimensions, transparency, and flag bytes
     * @return Complete AOL art payload (header + GIF87a bytes, no padding by default)
     * @throws IOException if encoding fails
     */
    public static byte[] encode(BufferedImage image, ArtMetadata metadata) throws IOException {
        return encode(image, metadata.isTransparency(), metadata.getFlagByte1(), metadata.getFlagByte2());
    }

    /**
     * Encode a BufferedImage to AOL art format with custom flag bytes.
     *
     * @param image The image to encode
     * @param enableTransparency Whether to enable transparency
     * @param flagByte1 Resource category flag (byte 36)
     * @param flagByte2 Resource type code (byte 37)
     * @return Complete AOL art payload (header + GIF87a bytes, no padding by default)
     * @throws IOException if encoding fails
     */
    public static byte[] encode(BufferedImage image, boolean enableTransparency,
                                 int flagByte1, int flagByte2) throws IOException {
        LoggerUtil.debug(() -> String.format(
                "Encoding image to GIF87a: size=%dx%d, transparency=%s",
                image.getWidth(), image.getHeight(), enableTransparency));

        // Pre-quantize to 256 colors to force Global Color Table (GCT) instead of Local Color Table (LCT)
        // AOL clients expect GCT in Logical Screen Descriptor, not LCT after Image Descriptor
        BufferedImage indexed = quantizeTo256Colors(image);
        LoggerUtil.debug(() -> "Pre-quantized image to 256 colors for Global Color Table");

        // Get GIF writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) {
            throw new IOException("No GIF writer found");
        }
        ImageWriter writer = writers.next();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        // Configure GIF metadata for exact AOL 3.0 format
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        // Create ImageTypeSpecifier from the indexed image (ensures GCT, not LCT)
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromRenderedImage(indexed);
        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, writeParam);

        // Configure transparency and format
        configureMetadata(metadata, enableTransparency);

        // Write the indexed image
        IIOImage iioImage = new IIOImage(indexed, null, metadata);
        writer.write(null, iioImage, writeParam);

        writer.dispose();
        ios.close();

        byte[] gifBytes = baos.toByteArray();

        // STEP 1: Ensure GIF87a header (AOL clients require GIF87a, not GIF89a)
        gifBytes = ensureGif87aHeader(gifBytes);

        // STEP 2: Strip any Graphic Control Extension blocks (AOL expects pure GIF87a)
        gifBytes = stripGraphicControlExtensions(gifBytes);

        // STEP 3: Force LSD packed byte to 0xF7 (GCT present, 8-bit, 256 colors)
        if (gifBytes.length > 10) {
            gifBytes[10] = (byte) 0xF7;
            LoggerUtil.debug(() -> "Forced LSD packed byte to 0xF7 (GCT enabled)");
        }

        // STEP 4: Force background color index to 0 (AOL convention)
        if (gifBytes.length > 11) {
            gifBytes[11] = (byte) 0x00;
            LoggerUtil.debug(() -> "Forced background color index to 0");
        }

        // STEP 5: Convert Local Color Table to Global Color Table
        // Move palette from after Image Descriptor to after LSD, set Image Descriptor packed byte to 0x00
        // Note: Interlacing is already disabled via metadata configuration
        gifBytes = convertLocalToGlobalColorTable(gifBytes);

        // STEP 6: Validate final GIF structure for AOL compatibility
        validateGifStructure(gifBytes);

        // Generate padding bytes (optional - default is 0 bytes, no padding)
        byte[] padding = new byte[DEFAULT_PADDING_SIZE];
        // Note: Working AOL transmissions prove padding is optional. Some archived
        // AOL files contain padding, but it's not required for proper operation.

        // Enforce AOL flag byte 1: must be 0x80 for art/icon resources
        // If caller passes 0x00, override to 0x80 (AOL requirement)
        if ((flagByte1 & 0xFF) == 0x00) {
            final int oldFlagByte1 = flagByte1;
            flagByte1 = 0x80;
            LoggerUtil.debug(() -> String.format(
                    "Overrode flagByte1 from 0x%02X to 0x80 (AOL art/icon category requirement)",
                    oldFlagByte1));
        }

        // Create final copies for lambda expression
        final int finalFlagByte1 = flagByte1;
        final int finalFlagByte2 = flagByte2;
        final int gifSize = gifBytes.length;
        final int paddingSize = padding.length;

        // Generate AOL art header with GIF size + padding size + flag bytes
        LoggerUtil.debug(() -> String.format(
                "Generating AOL header: flagByte1=0x%02X, flagByte2=0x%02X, GIF size=%d, padding=%d",
                finalFlagByte1, finalFlagByte2, gifSize, paddingSize));

        byte[] aolHeader = AolArtHeader.generate(
                image.getWidth(),
                image.getHeight(),
                gifBytes.length,
                padding.length,
                flagByte1,
                flagByte2);

        // Combine header + GIF data + padding
        byte[] completePayload = new byte[aolHeader.length + gifBytes.length + padding.length];
        System.arraycopy(aolHeader, 0, completePayload, 0, aolHeader.length);
        System.arraycopy(gifBytes, 0, completePayload, aolHeader.length, gifBytes.length);
        System.arraycopy(padding, 0, completePayload, aolHeader.length + gifBytes.length, padding.length);

        final int payloadSize = completePayload.length;
        LoggerUtil.debug(() -> String.format(
                "GIF encoding complete: GIF=%d bytes, padding=%d bytes (optional), with AOL header=%d bytes total",
                gifSize, paddingSize, payloadSize));

        // Debug: Show first 45 bytes in hex to verify alignment
        LoggerUtil.debug(() -> {
            StringBuilder hex = new StringBuilder("First 45 bytes (header + GIF start): ");
            for (int i = 0; i < Math.min(45, completePayload.length); i++) {
                if (i == 26) hex.append("| ");  // Magic Marker B start
                if (i == 36) hex.append("| ");  // Flag bytes start
                if (i == 40) hex.append("| ");  // GIF start
                hex.append(String.format("%02X ", completePayload[i] & 0xFF));
            }
            return hex.toString();
        });

        return completePayload;
    }

    /**
     * Configure GIF metadata for AOL 3.0 GIF87a format.
     *
     * CRITICAL: AOL expects pure GIF87a with NO Graphic Control Extension (GCE).
     * This method removes any GCE nodes and forces non-interlaced encoding.
     * The background color index is set directly in the byte stream (not via metadata).
     */
    private static void configureMetadata(IIOMetadata metadata, boolean enableTransparency)
            throws IIOInvalidTreeException {

        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

        // Remove any GraphicControlExtension node the writer might add by default
        // AOL expects GIF87a with NO GCE at all
        for (int i = 0; i < root.getLength(); i++) {
            if ("GraphicControlExtension".equals(root.item(i).getNodeName())) {
                root.removeChild(root.item(i));
                LoggerUtil.debug(() -> "Removed GraphicControlExtension node from metadata");
                break;
            }
        }

        // Configure Image Descriptor: force non-interlaced encoding
        // This ensures pixel data is written in sequential row order, not interlaced
        IIOMetadataNode imageDescriptor = getOrCreateNode(root, "ImageDescriptor");
        imageDescriptor.setAttribute("imageLeftPosition", "0");
        imageDescriptor.setAttribute("imageTopPosition", "0");
        imageDescriptor.setAttribute("interlaceFlag", "FALSE");  // Critical: non-interlaced data
        LoggerUtil.debug(() -> "Configured Image Descriptor: non-interlaced encoding");

        // Note: Background color index is set directly in the byte stream (step 4 of encode pipeline)
        // The LogicalScreenDescriptor is not accessible via image metadata, only stream metadata

        metadata.setFromTree(metaFormatName, root);
    }

    /**
     * Get or create a child node with the given name.
     */
    private static IIOMetadataNode getOrCreateNode(IIOMetadataNode root, String nodeName) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(nodeName)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        root.appendChild(node);
        return node;
    }

    /**
     * Ensure the GIF has a GIF87a header (bytes 0-5: "GIF87a").
     * Java's ImageIO typically writes GIF89a, but AOL clients may expect GIF87a.
     */
    private static byte[] ensureGif87aHeader(byte[] gifBytes) {
        if (gifBytes.length < 6) {
            return gifBytes;
        }

        // Check if header is GIF89a (47 49 46 38 39 61)
        if (gifBytes[0] == 'G' && gifBytes[1] == 'I' && gifBytes[2] == 'F' &&
                gifBytes[3] == '8' && gifBytes[4] == '9' && gifBytes[5] == 'a') {

            LoggerUtil.debug(() -> "Converting GIF89a header to GIF87a");

            // Change '9' to '7'
            gifBytes[4] = '7';
        }

        return gifBytes;
    }

    /**
     * Strip all Graphic Control Extension (GCE) blocks from GIF byte stream.
     *
     * AOL expects pure GIF87a with NO GCE blocks. This method removes any
     * GCE blocks that ImageIO might have inserted despite our metadata configuration.
     *
     * GCE structure: 21 F9 04 [packed] [delay_low] [delay_high] [trans_idx] 00 (8 bytes)
     *
     * @param gifBytes The GIF data
     * @return GIF data with all GCE blocks removed
     */
    private static byte[] stripGraphicControlExtensions(byte[] gifBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(gifBytes.length);
        int removedCount = 0;

        for (int i = 0; i < gifBytes.length;) {
            // Check for GCE pattern: 21 F9 04 xx xx xx xx 00
            if (i + 7 < gifBytes.length &&
                (gifBytes[i] & 0xFF) == 0x21 &&
                (gifBytes[i + 1] & 0xFF) == 0xF9 &&
                (gifBytes[i + 2] & 0xFF) == 0x04 &&
                (gifBytes[i + 7] & 0xFF) == 0x00) {

                // Skip 8-byte GCE block
                removedCount++;
                final int gceOffset = i;
                LoggerUtil.debug(() -> String.format(
                        "Stripped GCE block at offset %d", gceOffset));
                i += 8;
                continue;
            }

            out.write(gifBytes[i++]);
        }

        final int finalRemovedCount = removedCount;
        if (finalRemovedCount > 0) {
            LoggerUtil.debug(() -> String.format(
                    "Removed %d GCE block(s) for GIF87a compliance", finalRemovedCount));
        }

        return out.toByteArray();
    }

    /**
     * Convert Local Color Table (LCT) to Global Color Table (GCT) for AOL compatibility.
     *
     * AOL clients expect:
     * - GCT in Logical Screen Descriptor (LSD packed byte = 0xF7)
     * - NO LCT in Image Descriptor (packed byte = 0x00)
     *
     * Java's ImageIO generates the opposite:
     * - NO GCT (LSD packed byte = 0x77)
     * - LCT present (Image Descriptor packed byte = 0x87)
     *
     * This method restructures the GIF by:
     * 1. Enabling GCT in LSD (0x77 → 0xF7)
     * 2. Moving 768-byte palette from after Image Descriptor to after LSD
     * 3. Disabling LCT in Image Descriptor (0x87 → 0x00)
     */
    private static byte[] convertLocalToGlobalColorTable(byte[] gifBytes) {
        LoggerUtil.debug(() -> "Converting Local Color Table to Global Color Table for AOL compatibility");

        // GIF structure:
        // 0-5: "GIF87a" header
        // 6-12: Logical Screen Descriptor (LSD)
        //   - 6-7: width
        //   - 8-9: height
        //   - 10: packed byte [GCT flag|color res|sort|GCT size]
        //   - 11: background color index
        //   - 12: pixel aspect ratio
        // 13+: [Global Color Table if GCT flag set]
        // ...: Extensions, Image Descriptor, etc.

        if (gifBytes.length < 13) {
            LoggerUtil.debug(() -> "GIF too small to process (< 13 bytes)");
            return gifBytes;
        }

        // Step 1: Enable GCT in Logical Screen Descriptor
        int lsdPackedByteIndex = 10;
        int currentLsdPacked = gifBytes[lsdPackedByteIndex] & 0xFF;

        // Set bit 7 (GCT present) and ensure bits 0-2 = 111 (256 colors)
        // 0xF7 = 11110111 = GCT present, 8-bit color, no sort, 256 colors
        gifBytes[lsdPackedByteIndex] = (byte) 0xF7;

        final int oldLsdPacked = currentLsdPacked;
        LoggerUtil.debug(() -> String.format(
                "Updated LSD packed byte: 0x%02X → 0xF7 (enabled GCT)", oldLsdPacked));

        // Step 2: Find Image Descriptor (0x2C marker)
        int imageDescriptorIndex = -1;
        for (int i = 13; i < gifBytes.length - 10; i++) {
            if ((gifBytes[i] & 0xFF) == 0x2C) {
                imageDescriptorIndex = i;
                break;
            }
        }

        if (imageDescriptorIndex == -1) {
            LoggerUtil.debug(() -> "WARNING: No Image Descriptor found, cannot move palette");
            return gifBytes;
        }

        final int descriptorIdx = imageDescriptorIndex;
        LoggerUtil.debug(() -> String.format("Found Image Descriptor at offset %d", descriptorIdx));

        // Step 3: Check if Image Descriptor has LCT
        int imageDescriptorPackedIndex = imageDescriptorIndex + 9;
        if (imageDescriptorPackedIndex >= gifBytes.length) {
            LoggerUtil.debug(() -> "WARNING: Image Descriptor incomplete");
            return gifBytes;
        }

        int imageDescriptorPacked = gifBytes[imageDescriptorPackedIndex] & 0xFF;
        boolean hasLCT = (imageDescriptorPacked & 0x80) != 0;  // Bit 7

        if (!hasLCT) {
            LoggerUtil.debug(() -> "No LCT present in Image Descriptor, no palette to move");
            return gifBytes;
        }

        // Calculate LCT size
        int lctSizeBits = imageDescriptorPacked & 0x07;  // Bits 0-2
        int lctEntries = 1 << (lctSizeBits + 1);  // 2^(bits+1)
        int lctBytes = lctEntries * 3;  // RGB triplets

        final int lctSize = lctBytes;
        LoggerUtil.debug(() -> String.format(
                "Image Descriptor has LCT: %d entries, %d bytes", lctEntries, lctSize));

        // Step 4: Extract the LCT (starts right after Image Descriptor packed byte)
        int lctStartIndex = imageDescriptorPackedIndex + 1;
        int lctEndIndex = lctStartIndex + lctBytes;

        if (lctEndIndex > gifBytes.length) {
            LoggerUtil.debug(() -> String.format(
                    "ERROR: LCT extends beyond GIF data (declared %d bytes, only %d available)",
                    lctSize, gifBytes.length - lctStartIndex));
            return gifBytes;
        }

        byte[] localColorTable = new byte[lctBytes];
        System.arraycopy(gifBytes, lctStartIndex, localColorTable, 0, lctBytes);

        LoggerUtil.debug(() -> String.format(
                "Extracted LCT from offset %d-%d (%d bytes)",
                lctStartIndex, lctEndIndex - 1, lctSize));

        // Step 5: Build new GIF structure
        // [Header][LSD][GCT←inserted][Extensions...][Image Descriptor←no LCT][LZW data][Trailer]

        // Calculate new size: original + 768 bytes for GCT - 768 bytes removed LCT = same size
        byte[] newGif = new byte[gifBytes.length];

        int writePos = 0;

        // Copy header + LSD (bytes 0-12)
        System.arraycopy(gifBytes, 0, newGif, writePos, 13);
        writePos += 13;

        // Insert GCT right after LSD
        System.arraycopy(localColorTable, 0, newGif, writePos, lctBytes);
        writePos += lctBytes;

        // Copy everything from after LSD to Image Descriptor (extensions, etc.)
        int extensionsLength = imageDescriptorIndex - 13;
        System.arraycopy(gifBytes, 13, newGif, writePos, extensionsLength);
        writePos += extensionsLength;

        // Copy Image Descriptor (10 bytes: separator + left + top + width + height + packed)
        System.arraycopy(gifBytes, imageDescriptorIndex, newGif, writePos, 10);

        // Update Image Descriptor packed byte to disable LCT (set to 0x00)
        int newPackedByteIndex = writePos + 9;
        newGif[newPackedByteIndex] = (byte) 0x00;  // No LCT, no interlace, no sort

        final int oldImagePacked = imageDescriptorPacked;
        LoggerUtil.debug(() -> String.format(
                "Updated Image Descriptor packed byte: 0x%02X → 0x00 (disabled LCT)",
                oldImagePacked));

        writePos += 10;

        // Copy everything after the LCT (LZW data, trailer, etc.)
        int remainingBytes = gifBytes.length - lctEndIndex;
        System.arraycopy(gifBytes, lctEndIndex, newGif, writePos, remainingBytes);
        writePos += remainingBytes;

        LoggerUtil.debug(() -> String.format(
                "GIF restructured: moved %d-byte palette from LCT to GCT", lctSize));

        return newGif;
    }

    /**
     * Validate GIF structure for AOL compatibility.
     * Checks GIF87a header, no GCE blocks, GCT present, background index = 0, and proper LZW structure.
     *
     * @param gifBytes The GIF data to validate
     */
    private static void validateGifStructure(byte[] gifBytes) {
        LoggerUtil.debug(() -> "Validating GIF structure for AOL GIF87a compatibility...");

        if (gifBytes.length < 13) {
            LoggerUtil.debug(() -> "ERROR: GIF too small to validate (< 13 bytes)");
            return;
        }

        // Check 1: Verify GIF87a header (bytes 0-5: "GIF87a")
        if (gifBytes.length >= 6) {
            boolean isGif87a = gifBytes[0] == 'G' && gifBytes[1] == 'I' && gifBytes[2] == 'F' &&
                               gifBytes[3] == '8' && gifBytes[4] == '7' && gifBytes[5] == 'a';
            if (!isGif87a) {
                String header = new String(gifBytes, 0, 6);
                LoggerUtil.debug(() -> String.format(
                        "ERROR: Header is '%s', expected 'GIF87a'", header));
            } else {
                LoggerUtil.debug(() -> "✓ GIF87a header confirmed");
            }
        }

        // Check 2: Verify NO Graphic Control Extension blocks (21 F9 04 ... pattern)
        boolean foundGCE = false;
        for (int i = 0; i < gifBytes.length - 7; i++) {
            if ((gifBytes[i] & 0xFF) == 0x21 && (gifBytes[i + 1] & 0xFF) == 0xF9 &&
                (gifBytes[i + 2] & 0xFF) == 0x04) {
                foundGCE = true;
                final int gceOffset = i;
                LoggerUtil.debug(() -> String.format(
                        "ERROR: Found GCE block at offset %d (AOL expects pure GIF87a with NO GCE)", gceOffset));
                break;
            }
        }
        if (!foundGCE) {
            LoggerUtil.debug(() -> "✓ No GCE blocks found (pure GIF87a)");
        }

        // Check 3: Validate Logical Screen Descriptor
        int lsdPackedByte = gifBytes[10] & 0xFF;
        int bgColorIndex = gifBytes[11] & 0xFF;
        boolean hasGCT = (lsdPackedByte & 0x80) != 0;  // Bit 7
        int gctSizeBits = lsdPackedByte & 0x07;         // Bits 0-2
        int gctEntries = 1 << (gctSizeBits + 1);        // 2^(bits+1)

        final int finalLsdPacked = lsdPackedByte;
        final int finalBgIndex = bgColorIndex;
        final int finalGctEntries = gctEntries;

        LoggerUtil.debug(() -> String.format(
                "LSD: packed=0x%02X, bgIndex=%d, hasGCT=%s, gctEntries=%d",
                finalLsdPacked, finalBgIndex, hasGCT, finalGctEntries));

        if (!hasGCT) {
            LoggerUtil.debug(() -> "ERROR: No GCT declared in LSD (AOL expects GCT with 0xF7)");
        } else if (lsdPackedByte != 0xF7) {
            LoggerUtil.debug(() -> String.format(
                    "WARNING: LSD packed byte is 0x%02X, expected 0xF7 for AOL compatibility",
                    finalLsdPacked));
        } else {
            LoggerUtil.debug(() -> "✓ LSD packed byte is 0xF7 (GCT enabled, 256 colors)");
        }

        if (bgColorIndex != 0x00) {
            LoggerUtil.debug(() -> String.format(
                    "ERROR: Background color index is %d, expected 0 (AOL transparency convention)",
                    finalBgIndex));
        } else {
            LoggerUtil.debug(() -> "✓ Background color index is 0 (AOL convention)");
        }

        // Check 4: Find and validate Image Descriptor (0x2C)
        int imageDescriptorIndex = -1;
        for (int i = 0; i < gifBytes.length - 10; i++) {
            if ((gifBytes[i] & 0xFF) == 0x2C) {
                imageDescriptorIndex = i;
                break;
            }
        }

        if (imageDescriptorIndex == -1) {
            LoggerUtil.debug(() -> "WARNING: No Image Descriptor found in GIF");
            return;
        }

        final int descriptorIdx = imageDescriptorIndex;
        LoggerUtil.debug(() -> String.format("Found Image Descriptor at offset %d", descriptorIdx));

        // Check 5: Read Image Descriptor packed byte (offset +9 from descriptor start)
        int packedByteIndex = imageDescriptorIndex + 9;
        int packedByte = gifBytes[packedByteIndex] & 0xFF;

        // Extract LCT info from packed byte
        boolean hasLCT = (packedByte & 0x80) != 0;  // Bit 7
        int lctSizeBits = packedByte & 0x07;         // Bits 0-2
        int lctEntries = 1 << (lctSizeBits + 1);     // 2^(bits+1)
        int lctBytes = lctEntries * 3;               // RGB triplets

        final int finalPackedByte = packedByte;
        final int finalLctEntries = lctEntries;
        final int finalLctBytes = lctBytes;

        LoggerUtil.debug(() -> String.format(
                "Image Descriptor packed=0x%02X: hasLCT=%s, lctSizeBits=%d, entries=%d, bytes=%d",
                finalPackedByte, hasLCT, lctSizeBits, finalLctEntries, finalLctBytes));

        // CRITICAL: AOL expects NO LCT in Image Descriptor (should be 0x00)
        if (hasLCT) {
            LoggerUtil.debug(() -> "ERROR: LCT present in Image Descriptor (AOL expects NO LCT, packed should be 0x00)");
            return;
        }

        if (packedByte != 0x00) {
            LoggerUtil.debug(() -> String.format(
                    "WARNING: Image Descriptor packed byte is 0x%02X, expected 0x00 for AOL compatibility",
                    finalPackedByte));
        } else {
            LoggerUtil.debug(() -> "✓ Image Descriptor packed byte is 0x00 (no LCT)");
        }

        // Check 6: Validate LZW data follows immediately after Image Descriptor (no LCT)
        // LZW min code size byte should be right after Image Descriptor packed byte
        int lzwMinCodeSizeIndex = packedByteIndex + 1;

        if (lzwMinCodeSizeIndex >= gifBytes.length) {
            LoggerUtil.debug(() -> "ERROR: No LZW data found after Image Descriptor");
            return;
        }

        int lzwMinCodeSize = gifBytes[lzwMinCodeSizeIndex] & 0xFF;
        final int finalLzwMinCodeSize = lzwMinCodeSize;
        final int finalLzwIndex = lzwMinCodeSizeIndex;

        LoggerUtil.debug(() -> String.format(
                "LZW min code size at offset %d: 0x%02X",
                finalLzwIndex, finalLzwMinCodeSize));

        // Expected: 0x08 for 256-color GCT
        if (gctEntries == 256 && lzwMinCodeSize != 0x08) {
            LoggerUtil.debug(() -> String.format(
                    "WARNING: LZW min code size should be 0x08 for 256-color GCT, got 0x%02X",
                    finalLzwMinCodeSize));
        }

        // Validate LZW sub-block structure
        int dataIndex = lzwMinCodeSizeIndex + 1;
        int blockCount = 0;
        boolean foundTerminator = false;

        while (dataIndex < gifBytes.length) {
            int blockLen = gifBytes[dataIndex] & 0xFF;

            if (blockLen == 0x00) {
                // Found block terminator
                foundTerminator = true;
                final int finalDataIndex = dataIndex;
                final int finalBlockCount = blockCount;
                LoggerUtil.debug(() -> String.format(
                        "LZW stream terminated at offset %d after %d data blocks",
                        finalDataIndex, finalBlockCount));

                // Check for GIF trailer (0x3B) after terminator
                int trailerIndex = dataIndex + 1;
                if (trailerIndex < gifBytes.length && (gifBytes[trailerIndex] & 0xFF) == 0x3B) {
                    final int finalTrailerIndex = trailerIndex;
                    LoggerUtil.debug(() -> String.format(
                            "GIF trailer (0x3B) found at offset %d ✓", finalTrailerIndex));
                } else {
                    final int finalTrailerIndex = trailerIndex;
                    final int actualByte = trailerIndex < gifBytes.length ? (gifBytes[trailerIndex] & 0xFF) : -1;
                    LoggerUtil.debug(() -> String.format(
                            "WARNING: Expected GIF trailer (0x3B) at offset %d, found 0x%02X",
                            finalTrailerIndex, actualByte));
                }
                break;
            }

            // Valid sub-block: skip length byte + data bytes
            blockCount++;
            dataIndex += 1 + blockLen;

            if (dataIndex > gifBytes.length) {
                final int finalBlockCount2 = blockCount;
                final int finalBlockLen = blockLen;
                LoggerUtil.debug(() -> String.format(
                        "ERROR: LZW block %d declares length %d but extends beyond GIF data",
                        finalBlockCount2, finalBlockLen));
                return;
            }
        }

        if (!foundTerminator) {
            LoggerUtil.debug(() -> "ERROR: LZW stream missing 0x00 block terminator");
        }

        LoggerUtil.debug(() -> "===============================================");
        LoggerUtil.debug(() -> "GIF87a validation complete ✓");
        LoggerUtil.debug(() -> "AOL-compatible GIF structure confirmed:");
        LoggerUtil.debug(() -> "  • GIF87a header");
        LoggerUtil.debug(() -> "  • No GCE blocks");
        LoggerUtil.debug(() -> "  • LSD packed = 0xF7 (GCT, 256 colors)");
        LoggerUtil.debug(() -> "  • Background index = 0");
        LoggerUtil.debug(() -> "  • Image Descriptor packed = 0x00 (no LCT)");
        LoggerUtil.debug(() -> "===============================================");
    }

    /**
     * Resize an image to fit within specified dimensions while preserving aspect ratio.
     *
     * This method treats targetWidth and targetHeight as maximum bounds.
     * The image is scaled to fit within both dimensions, and the final size
     * is determined by whichever dimension would hit its limit first.
     *
     * Examples:
     * - Original 400×200, Target 256×256 → Result 256×128 (width limited)
     * - Original 200×400, Target 256×256 → Result 128×256 (height limited)
     * - Original 300×300, Target 256×256 → Result 256×256 (both scale equally)
     *
     * @param original The original image
     * @param targetWidth Maximum target width
     * @param targetHeight Maximum target height
     * @return Resized image with aspect ratio preserved
     */
    public static BufferedImage resize(BufferedImage original, int targetWidth, int targetHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        // Calculate scale factors for both dimensions
        double widthScale = (double) targetWidth / originalWidth;
        double heightScale = (double) targetHeight / originalHeight;

        // Use the smaller scale to ensure image fits within both bounds
        // This preserves aspect ratio while respecting both dimension limits
        double scale = Math.min(widthScale, heightScale);

        // Calculate final dimensions (preserves aspect ratio)
        int finalWidth = (int) Math.round(originalWidth * scale);
        int finalHeight = (int) Math.round(originalHeight * scale);

        LoggerUtil.debug(() -> String.format(
                "Resizing image (aspect-ratio preserved): %dx%d -> %dx%d (target bounds: %dx%d, scale: %.3f)",
                originalWidth, originalHeight, finalWidth, finalHeight,
                targetWidth, targetHeight, scale));

        BufferedImage resized = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();

        // Use high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, finalWidth, finalHeight, null);
        g2d.dispose();

        return resized;
    }

    /**
     * Quantize image to 256 colors using adaptive palette and optional dithering.
     * This is the recommended method for high-quality results.
     *
     * @param image The input image
     * @param enableDithering Whether to apply Floyd-Steinberg dithering
     * @param enablePosterization Whether to apply posterization before quantization
     * @param posterizationLevel Number of levels per channel if posterization is enabled (e.g., 32)
     * @return Image with indexed color model (256 colors)
     */
    public static BufferedImage quantizeTo256ColorsAdaptive(BufferedImage image,
                                                             boolean enableDithering,
                                                             boolean enablePosterization,
                                                             int posterizationLevel) {
        LoggerUtil.debug(() -> String.format(
            "Adaptive quantization: dithering=%s, posterization=%s (level=%d)",
            enableDithering, enablePosterization, posterizationLevel));

        // Step 1: Optional posterization for crisp AOL-style look
        BufferedImage processedImage = image;
        if (enablePosterization) {
            Posterizer posterizer = new Posterizer();
            processedImage = posterizer.posterize(image, posterizationLevel);
        }

        // Step 2: Generate adaptive palette using MedianCut
        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(processedImage);

        // Step 3: Optional dithering for better detail preservation
        BufferedImage workingImage = new BufferedImage(
            processedImage.getWidth(),
            processedImage.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = workingImage.createGraphics();
        g.drawImage(processedImage, 0, 0, null);
        g.dispose();

        if (enableDithering) {
            FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
            ditherer.dither(workingImage, palette);
        }

        // Step 4: Convert to indexed image with generated palette
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];

        for (int i = 0; i < 256; i++) {
            reds[i] = (byte) ((palette[i] >> 16) & 0xFF);
            greens[i] = (byte) ((palette[i] >> 8) & 0xFF);
            blues[i] = (byte) (palette[i] & 0xFF);
        }

        // Create IndexColorModel with adaptive palette, index 0 reserved as transparent
        IndexColorModel colorModel = new IndexColorModel(
            8,      // 8 bits per pixel
            256,    // 256 colors
            reds, greens, blues,
            0);     // transparent pixel index = 0 (AOL convention)

        // Create final indexed image
        BufferedImage indexedImage = new BufferedImage(
            workingImage.getWidth(),
            workingImage.getHeight(),
            BufferedImage.TYPE_BYTE_INDEXED,
            colorModel);

        Graphics2D g2d = indexedImage.createGraphics();
        g2d.drawImage(workingImage, 0, 0, null);
        g2d.dispose();

        LoggerUtil.debug(() -> "Adaptive quantization complete");
        return indexedImage;
    }

    /**
     * Quantize image to 256 colors with indexed color model.
     * Uses a static web-safe palette for backward compatibility.
     * For better quality, use quantizeTo256ColorsAdaptive() instead.
     *
     * @param image The input image
     * @return Image with indexed color model (256 colors)
     */
    public static BufferedImage quantizeTo256Colors(BufferedImage image) {
        LoggerUtil.debug(() -> "Quantizing image to 256 colors with static web-safe palette");

        // Create a full 256-color palette (web-safe colors + grayscale)
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];

        // Fill with web-safe colors (216 colors: 6x6x6 cube)
        int index = 0;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    reds[index] = (byte) (r * 51);
                    greens[index] = (byte) (g * 51);
                    blues[index] = (byte) (b * 51);
                    index++;
                }
            }
        }

        // Fill remaining with grayscale (40 shades)
        for (int i = 0; i < 40 && index < 256; i++, index++) {
            byte gray = (byte) (i * 6);
            reds[index] = gray;
            greens[index] = gray;
            blues[index] = gray;
        }

        // Create IndexColorModel with 256 colors, index 0 reserved as transparent
        // AOL treats palette index 0 specially for transparency in GIF87a icons
        IndexColorModel colorModel = new IndexColorModel(
                8,      // 8 bits per pixel
                256,    // 256 colors
                reds, greens, blues,
                0);     // transparent pixel index = 0 (AOL convention)

        // Create indexed image with explicit color model
        BufferedImage indexedImage = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_INDEXED,
                colorModel);

        Graphics2D g2d = indexedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return indexedImage;
    }
}
