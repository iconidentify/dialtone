/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import java.io.ByteArrayOutputStream;

/**
 * XFER download encoding for file transfer.
 *
 * <p>Uses escape-only encoding (no RLE compression for MVP):
 * <ul>
 *   <li>DL_ESC (0x5D) - Escape marker</li>
 *   <li>DL_RUN (0x5B) - Run-length marker (reserved, not used for MVP)</li>
 *   <li>DL_XOR (0x55) - XOR mask for escaped bytes</li>
 * </ul>
 *
 * <p>Bytes that must be escaped: 0x5B, 0x5D, 0x0D, 0x8D
 * <br>Encoding: DL_ESC + (byte ^ DL_XOR)
 */
public final class XferEncoder {

    /** Escape marker byte */
    public static final byte DL_ESC = 0x5D;

    /** Run-length marker byte (reserved for future use) */
    public static final byte DL_RUN = 0x5B;

    /** XOR mask for escaped bytes */
    public static final byte DL_XOR = 0x55;

    /** Carriage return - must be escaped */
    private static final byte CR = 0x0D;

    /** High-bit carriage return - must be escaped */
    private static final byte CR_HIGH = (byte) 0x8D;

    private XferEncoder() {}

    /**
     * Check if a byte needs to be escaped in the XFER encoding.
     *
     * @param b the byte to check
     * @return true if the byte must be escaped
     */
    public static boolean needsEscape(byte b) {
        return b == DL_RUN || b == DL_ESC || b == CR || b == CR_HIGH;
    }

    /**
     * Encode raw file data using XFER escape encoding.
     *
     * <p>Escape-only encoding (no RLE):
     * <ul>
     *   <li>If byte needs escaping: emit DL_ESC, then (byte ^ DL_XOR)</li>
     *   <li>Otherwise: emit byte as-is</li>
     * </ul>
     *
     * @param data raw file data
     * @return encoded data suitable for F7/F8/F9 token payloads
     */
    public static byte[] encode(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + data.length / 10);

        for (byte b : data) {
            if (needsEscape(b)) {
                out.write(DL_ESC & 0xFF);
                out.write((b ^ DL_XOR) & 0xFF);
            } else {
                out.write(b & 0xFF);
            }
        }

        return out.toByteArray();
    }

    /**
     * Decode XFER-encoded data back to raw bytes.
     *
     * <p>Reverses escape encoding:
     * <ul>
     *   <li>If byte is DL_ESC: read next byte, XOR with DL_XOR</li>
     *   <li>Otherwise: emit byte as-is</li>
     * </ul>
     *
     * @param encoded encoded data from F7/F8/F9 token payloads
     * @return decoded raw file data
     */
    public static byte[] decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length);
        boolean escapeActive = false;

        for (byte b : encoded) {
            if (escapeActive) {
                out.write((b ^ DL_XOR) & 0xFF);
                escapeActive = false;
            } else if (b == DL_ESC) {
                escapeActive = true;
            } else {
                out.write(b & 0xFF);
            }
        }

        return out.toByteArray();
    }
}
