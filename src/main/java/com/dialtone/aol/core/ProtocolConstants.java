/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.aol.core;

/**
 * Core AOL protocol constants and helpers (single source of truth).
 */
public final class ProtocolConstants {

    /**
     * First exact 9-byte pong after 0x5A 0x8D keepalive (Mac OS 9 keepalive handshake).
     * Server must reply with TWO exact frames during this handshake.
     */
    public static final String MAC_KEEPALIVE_PONG = "5ab71100037f7f240d";
    /**
     * SD Token sent back-to-back with the keepalive pong during the Mac OS 9 handshake.
     */
    public static final String MAC_HANDSHAKE = "5a88e9000b107f20534454d1f68b00000d";

    /**
     * Windows keepalive pong response (analogous to Mac keepalive pong).
     * Server sends this in response to Windows client init packet (0xA3 with length 52).
     */
    public static final String WINDOWS_KEEPALIVE_PONG = "5ab71100037f7f240d";

    /**
     * Windows SD Token handshake (analogous to Mac SD token).
     * Sent back-to-back with Windows keepalive pong during Windows client init.
     */
    public static final String WINDOWS_HANDSHAKE = "5a88e9000b107f20534454d1f68b00000d";

    /**
     * Precanned response for ff (0x6666) token - guest login ACK.
     */
    public static final String PRECANNED_FF_RESPONSE = "415400000001000A000101000A310000";

    /**
     * Precanned response for fh (0x6668) token - join chat room.
     */
    public static final String PRECANNED_FH_RESPONSE = "41540000002000144F000001000107546573742043686174";

    /**
     * Precanned response for pE token - signoff.
     */
    public static final String PRECANNED_PE_RESPONSE = "5A 2A 2A 00 07 7F 7F 20 58 53 47 6F 6F 64 62 79 65 21 0D";

    /**
     * Precanned response for f2 (0x6632) token - echo chat message (empty response).
     */
    public static final String PRECANNED_F2_RESPONSE = "";

    private ProtocolConstants() {}

    // Magic and masks
    public static final int MAGIC = 0x5A;
    public static final int BYTE_MASK = 0xFF;
    public static final int AOL_FRAME_MAGIC = MAGIC;
    public static final int AOL_FRAME_MAGIC_BYTE = MAGIC;

    // ACK type masks
    public static final int AOL_ACK_TYPE_MASK = 0xF0;
    public static final int AOL_ACK_TYPE_BASE = 0xA0;

    // Header indexes (offsets)
    public static final int IDX_MAGIC = 0;
    public static final int IDX_CRC_HI = 1;
    public static final int IDX_CRC_LO = 2;
    public static final int IDX_LEN_HI = 3;
    public static final int IDX_LEN_LO = 4;
    public static final int IDX_TX = 5;
    public static final int IDX_RX = 6;
    public static final int IDX_TYPE = 7;
    public static final int IDX_TOKEN = 8;

    // Sizes
    public static final int MIN_FRAME_SIZE = 6;     // magic+crc(2)+len(2) == 5? plus one more? kept for compatibility
    public static final int SHORT_FRAME_SIZE = 9;   // 9-byte control frame
    public static final int MIN_FULL_FRAME_SIZE = 10; // header with type + at least 2 byte token

    // Character ranges
    public static final int PRINTABLE_CHAR_MIN = 32;
    public static final int PRINTABLE_CHAR_MAX = 127;

    // Helpers
    public static boolean isAolFrame(byte[] data) {
        return data != null && data.length > 0 && (data[0] & BYTE_MASK) == MAGIC;
    }

    /**
     * Returns true if the value is printable ASCII.
     */
    public static boolean isPrintableChar(int c) {
        return c >= PRINTABLE_CHAR_MIN && c < PRINTABLE_CHAR_MAX;
    }

    /**
     * Returns true if the character is printable ASCII.
     */
    public static boolean isPrintableChar(char c) {
        return c >= PRINTABLE_CHAR_MIN && c < PRINTABLE_CHAR_MAX;
    }

    public static int u8(byte[] a, int i) {
        bounds(a, i, 1);
        return a[i] & BYTE_MASK;
    }

    public static int u16be(byte[] a, int i) {
        bounds(a, i, 2);
        return ((a[i] & BYTE_MASK) << 8) | (a[i + 1] & BYTE_MASK);
    }

    public static void bounds(byte[] a, int off, int len) {
        if (a == null) throw new IllegalArgumentException("null array");
        if (off < 0 || len < 0 || off + len > a.length) {
            throw new IndexOutOfBoundsException("off=" + off + " len=" + len + " a.length=" + a.length);
        }
    }
}


