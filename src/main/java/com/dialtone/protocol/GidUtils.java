/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

/**
 * Utility class for GID (Global ID) operations.
 * Provides consistent conversion between raw bytes, integer representation,
 * and AOL display format across f1, f2, K1, and other token handlers.
 *
 * GID Format:
 * - 32-bit integer in big-endian byte order
 * - Display format: "byte3-byte2-word" (e.g., "1-0-1333") or "byte2-word" if byte3 is 0 (e.g., "40-47544")
 */
public class GidUtils {

    /**
     * Convert 4 bytes at the specified offset to a 32-bit GID (big-endian).
     * Used for extracting GID from raw packet data (f1, f2 tokens).
     *
     * @param packet The byte array containing the GID
     * @param offset The offset where the 4-byte GID starts
     * @return 32-bit GID value
     */
    public static int bytesToGid(byte[] packet, int offset) {
        return ((packet[offset] & 0xFF) << 24) |
               ((packet[offset + 1] & 0xFF) << 16) |
               ((packet[offset + 2] & 0xFF) << 8) |
               (packet[offset + 3] & 0xFF);
    }

    /**
     * Convert a 4-byte array to a 32-bit GID (big-endian).
     * Used for extracting GID from parsed de_data (K1 token).
     *
     * @param bytes The 4-byte array containing the GID
     * @return 32-bit GID value
     */
    public static int bytesToGid(byte[] bytes) {
        return bytesToGid(bytes, 0);
    }

    /**
     * Convert 32-bit GID to AOL display format.
     * Based on FormatGID() from BUTLER/gid.c.
     *
     * Format rules:
     * - If byte3 (MSB) is non-zero: "byte3-byte2-word" (e.g., "1-0-1333")
     * - If byte3 is zero: "byte2-word" (e.g., "40-47544")
     *
     * @param gid 32-bit GID value
     * @return Formatted string like "40-47544" or "1-0-1333"
     */
    public static String formatToDisplay(int gid) {
        int byte3 = (gid >> 24) & 0xFF;  // Most significant byte
        int byte2 = (gid >> 16) & 0xFF;  // Second byte
        int word = gid & 0xFFFF;         // Lower 16 bits (word)

        if (byte3 != 0) {
            return String.format("%d-%d-%d", byte3, byte2, word);
        }
        return String.format("%d-%d", byte2, word);
    }

    /**
     * Parse AOL display format GID string back to 32-bit integer.
     * Inverse of formatToDisplay().
     *
     * @param displayGid GID in display format (e.g., "40-47544" or "1-0-1333")
     * @return 32-bit GID value, or 0 if parsing fails
     */
    public static int parseFromDisplay(String displayGid) {
        if (displayGid == null || displayGid.isEmpty()) {
            return 0;
        }

        String[] parts = displayGid.split("-");
        try {
            if (parts.length == 2) {
                // Format: "byte2-word"
                int byte2 = Integer.parseInt(parts[0]);
                int word = Integer.parseInt(parts[1]);
                return (byte2 << 16) | word;
            } else if (parts.length == 3) {
                // Format: "byte3-byte2-word"
                int byte3 = Integer.parseInt(parts[0]);
                int byte2 = Integer.parseInt(parts[1]);
                int word = Integer.parseInt(parts[2]);
                return (byte3 << 24) | (byte2 << 16) | word;
            }
        } catch (NumberFormatException e) {
            // Fall through to return 0
        }
        return 0;
    }
}

