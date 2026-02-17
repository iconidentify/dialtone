/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Generates the 40-byte protocol-specific header that must precede GIF data
 * for art assets in v3 DOD (Download on Demand) requests.
 *
 * The header contains image dimensions, size information, and format markers
 * required by the client for proper image rendering.
 */
public class AolArtHeader {

    private static final int HEADER_SIZE = 40;

    /**
     * Magic constant for GIF format (bytes 14-15 in header).
     * Used for standard GIF images and BMP files.
     */
    public static final int MAGIC_GIF = 0x0205;

    /**
     * Magic constant for AOL .ART format (bytes 14-15 in header).
     * Used for native AOL art files with "JG" signature.
     */
    public static final int MAGIC_ART = 0x0209;

    /**
     * Generate a 40-byte protocol art header with custom flag bytes.
     * Uses default magic constant for GIF format (0x0205).
     *
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param dataSize Size of the image data in bytes
     * @param paddingSize Size of padding after image data in bytes
     * @param flagByte1 Resource category flag (byte 36)
     * @param flagByte2 Resource type code (byte 37)
     * @return 40-byte header array
     */
    public static byte[] generate(int width, int height, int dataSize, int paddingSize,
                                   int flagByte1, int flagByte2) {
        return generate(width, height, dataSize, paddingSize, flagByte1, flagByte2, MAGIC_GIF);
    }

    /**
     * Generate a 40-byte protocol art header with custom flag bytes and magic constant.
     *
     * Header structure (all multi-byte values are little-endian):
     * - Bytes 0-3: Version marker (01 00 01 00)
     * - Bytes 4-5: Flag field (01 00)
     * - Bytes 6-7: Size A = (data_size + Padding_size) + 36 [UNSIGNED SHORT: 0-65535]
     * - Bytes 8-11: Variable flags (00 00 01 00)
     * - Bytes 12-13: Unknown field (00 00)
     * - Bytes 14-15: Magic Constant (format identifier): 0x0205 for GIF/BMP, 0x0209 for ART
     * - Bytes 16-17: Image width
     * - Bytes 18-19: Image height
     * - Bytes 20-21: Zero padding
     * - Bytes 22-23: Size B = data_size + Padding_size [UNSIGNED SHORT: 0-65535]
     * - Bytes 24-25: Null padding
     * - Bytes 26-35: Magic Marker B (24 00 00 00 00 00 00 00 00 00)
     * - Bytes 36-39: Flag bytes (varies by resource type)
     *
     * Mathematical relationships:
     *   R2D2_Length = Size_A + 4
     *   Size_A = Size_B + 36
     *   Size_B = data_size + Padding_size
     *
     * IMPORTANT: Size A and Size B are unsigned 16-bit values, allowing range 0-65,535 bytes.
     * While Java's putShort() uses signed shorts (-32768 to 32767), the client interprets
     * these fields as unsigned, so values from 32768-65535 are supported via two's complement.
     * Safe limit: Keep data + padding under 60KB to leave safety margin for 64KB unsigned limit.
     *
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param dataSize Size of the image data in bytes
     * @param paddingSize Size of padding after image data in bytes
     * @param flagByte1 Resource category flag (byte 36)
     * @param flagByte2 Resource type code (byte 37)
     * @param magicConstant Format identifier (bytes 14-15): MAGIC_GIF (0x0205) or MAGIC_ART (0x0209)
     * @return 40-byte header array
     */
    public static byte[] generate(int width, int height, int dataSize, int paddingSize,
                                   int flagByte1, int flagByte2, int magicConstant) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Calculate Size B (bytes 22-23): image data + padding
        int sizeB = dataSize + paddingSize;

        // Calculate Size A (bytes 6-7): Size B + 36
        int sizeA = sizeB + 36;

        // Validate size limits (Size B and Size A are unsigned shorts: max 65535)
        if (sizeB > 65535) {
            throw new IllegalArgumentException(String.format(
                "Data + padding size (%d bytes) exceeds unsigned short limit (65535 bytes). " +
                "Reduce image dimensions or enable higher compression.",
                sizeB));
        }
        if (sizeB > 32767) {
            // Warning: exceeds signed short limit, but still valid as unsigned
            com.dialtone.utils.LoggerUtil.warn(String.format(
                "Size B (%d bytes) exceeds signed short limit (32767) but is valid as unsigned short (max 65535). " +
                "Data size: %d bytes, padding: %d bytes",
                sizeB, dataSize, paddingSize));
        }
        if (sizeB > 60000) {
            // Critical warning: approaching unsigned limit
            com.dialtone.utils.LoggerUtil.warn(String.format(
                "Size B (%d bytes) is approaching unsigned short limit (65535). " +
                "Consider reducing image dimensions. Data size: %d bytes, padding: %d bytes",
                sizeB, dataSize, paddingSize));
        }

        // Bytes 0-5: Format markers
        buffer.putShort((short) 0x0001);  // 01 00 (bytes 0-1: Version marker part 1)
        buffer.putShort((short) 0x0001);  // 01 00 (bytes 2-3: Version marker part 2)
        buffer.putShort((short) 0x0001);  // 01 00 (bytes 4-5: Flag field)

        // Bytes 6-7: Size A (little-endian unsigned short: 0-65535)
        buffer.putShort((short) sizeA);  // Cast to signed short, but interpreted as unsigned by client

        // Bytes 8-13: Unknown markers
        buffer.putShort((short) 0x0000);  // 00 00
        buffer.putShort((short) 0x0001);  // 01 00
        buffer.putShort((short) 0x0000);  // 00 00

        // Bytes 14-15: Magic constant (format identifier)
        buffer.putShort((short) magicConstant);  // 0x0205 for GIF/BMP, 0x0209 for ART

        // Bytes 16-17: Image width (little-endian)
        buffer.putShort((short) width);

        // Bytes 18-19: Image height (little-endian)
        buffer.putShort((short) height);

        // Bytes 20-21: Zero padding
        buffer.putShort((short) 0x0000);

        // Bytes 22-23: Size B = GIF data size + padding size (little-endian unsigned short: 0-65535)
        buffer.putShort((short) sizeB);  // Cast to signed short, but interpreted as unsigned by client

        // Bytes 24-25: Null padding
        buffer.putShort((short) 0x0000);  // 00 00

        // Bytes 26-35: Magic Marker B (0x24 followed by 9 null bytes)
        buffer.put((byte) 0x24);                      // Byte 26: Magic marker start
        for (int i = 0; i < 9; i++) {
            buffer.put((byte) 0x00);                  // Bytes 27-35: 9 zeros
        }

        // Bytes 36-39: Flag bytes (resource type indicators)
        buffer.put((byte) (flagByte1 & 0xFF));  // Byte 36: Resource category flag
        buffer.put((byte) (flagByte2 & 0xFF));  // Byte 37: Resource type code
        buffer.putShort((short) 0x0000);        // Bytes 38-39: Reserved

        return buffer.array();
    }

    /**
     * Generate a 40-byte protocol art header for GIF data with default flag bytes.
     * Uses default flag bytes: 0x80 (most common category) and 0xFD (most common type).
     *
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param gifDataSize Size of the GIF data in bytes
     * @param paddingSize Size of padding after GIF data in bytes
     * @return 40-byte header array
     */
    public static byte[] generate(int width, int height, int gifDataSize, int paddingSize) {
        return generate(width, height, gifDataSize, paddingSize, 0x80, 0xFD);
    }

    /**
     * Generate a 40-byte protocol art header for GIF data (backward compatible).
     * This overload assumes no padding (paddingSize = 0) and uses default flag bytes.
     *
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param gifDataSize Size of the GIF data in bytes
     * @return 40-byte header array
     * @deprecated Use {@link #generate(int, int, int, int)} with explicit padding size
     */
    @Deprecated
    public static byte[] generate(int width, int height, int gifDataSize) {
        return generate(width, height, gifDataSize, 0, 0x80, 0xFD);
    }

    /**
     * Get the size of the protocol art header.
     * @return Header size in bytes (always 40)
     */
    public static int getHeaderSize() {
        return HEADER_SIZE;
    }
}
