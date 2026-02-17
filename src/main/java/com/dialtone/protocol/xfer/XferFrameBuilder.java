/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.PacketType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Builds XFER protocol frames for file transfer.
 *
 * <p>Creates complete P3 DATA frames with proper headers for:
 * <ul>
 *   <li>tj token (0x746A) - File description with TJ_IN struct</li>
 *   <li>tf token (0x7466) - Start transfer with TF_IN struct</li>
 *   <li>F7/F9 tokens - Data chunks (F9 for final chunk)</li>
 * </ul>
 *
 * <p>Frame header layout (10 bytes):
 * <pre>
 * [0]     Magic (0x5A)
 * [1-2]   CRC (filled by restamp)
 * [3-4]   Length (filled by restamp)
 * [5]     TX seq (filled by restamp)
 * [6]     RX seq (filled by restamp)
 * [7]     Type (0x20 for DATA)
 * [8-9]   Token bytes
 * </pre>
 */
public final class XferFrameBuilder {

    // Token bytes
    public static final byte TOKEN_TJ_HI = 0x74;  // 't'
    public static final byte TOKEN_TJ_LO = 0x6A;  // 'j'
    public static final byte TOKEN_TF_HI = 0x74;  // 't'
    public static final byte TOKEN_TF_LO = 0x66;  // 'f'
    public static final byte TOKEN_F7_HI = 0x46;  // 'F'
    public static final byte TOKEN_F7_LO = 0x37;  // '7'
    public static final byte TOKEN_F9_HI = 0x46;  // 'F'
    public static final byte TOKEN_F9_LO = 0x39;  // '9'

    // TJ_IN struct size (67 bytes)
    public static final int TJ_IN_SIZE = 67;
    // TF_IN struct size (87 bytes)
    public static final int TF_IN_SIZE = 87;

    // TF_IN flags
    public static final byte TF_FLAG_PROGRESS_METER = 0x20;

    // TF_IN separator character
    public static final byte TF_SEP_CHAR = (byte) 0x90;

    // Frame terminator
    private static final byte FRAME_TERMINATOR = 0x0D;

    private XferFrameBuilder() {}

    /**
     * Build a complete P3 frame with the given token and payload.
     *
     * @param tokenHi high byte of token
     * @param tokenLo low byte of token
     * @param payload struct/data payload (after token bytes)
     * @return complete P3 DATA frame ready for Pacer
     */
    public static byte[] buildFrame(byte tokenHi, byte tokenLo, byte[] payload) {
        boolean needsTerminator = payload == null || payload.length == 0 ||
                                  payload[payload.length - 1] != FRAME_TERMINATOR;

        int payloadLen = payload != null ? payload.length : 0;
        int totalLength = ProtocolConstants.MIN_FULL_FRAME_SIZE + payloadLen +
                         (needsTerminator ? 1 : 0);

        byte[] frame = new byte[totalLength];

        // Header
        frame[ProtocolConstants.IDX_MAGIC] = (byte) ProtocolConstants.AOL_FRAME_MAGIC;
        // CRC (1-2) left as 0 - restamp will compute
        // Length (3-4) left as 0 - restamp will compute
        // TX (5) left as 0 - restamp will set
        // RX (6) left as 0 - restamp will set
        frame[ProtocolConstants.IDX_TYPE] = (byte) PacketType.DATA.getValue();
        frame[ProtocolConstants.IDX_TOKEN] = tokenHi;
        frame[ProtocolConstants.IDX_TOKEN + 1] = tokenLo;

        // Payload
        if (payloadLen > 0) {
            System.arraycopy(payload, 0, frame, ProtocolConstants.MIN_FULL_FRAME_SIZE, payloadLen);
        }

        // Terminator
        if (needsTerminator) {
            frame[totalLength - 1] = FRAME_TERMINATOR;
        }

        return frame;
    }

    /**
     * Build TJ_IN struct (file description).
     *
     * <p>TJ_IN layout (67 bytes):
     * <pre>
     * [0]      bTypeOfFile (use 0x00)
     * [1-3]    fileID[3] (3-byte file ID)
     * [4-7]    dwCreateDate (32-bit, big-endian)
     * [8-11]   dwByteCount (32-bit, BIG-ENDIAN)
     * [12-66]  szText[55] (library + subject, null-separated)
     * </pre>
     *
     * @param fileType type of file (use 0x00)
     * @param fileId 3-byte file ID
     * @param createDate creation date (seconds since epoch or AOL epoch)
     * @param fileSize file size in bytes
     * @param library library name (e.g., "Applications")
     * @param subject subject/title (e.g., "Tiny example file")
     * @return TJ_IN struct bytes
     */
    public static byte[] buildTjStruct(int fileType, byte[] fileId, int createDate,
                                        int fileSize, String library, String subject) {
        byte[] struct = new byte[TJ_IN_SIZE];

        // bTypeOfFile
        struct[0] = (byte) fileType;

        // fileID[3]
        if (fileId != null && fileId.length >= 3) {
            System.arraycopy(fileId, 0, struct, 1, 3);
        }

        // dwCreateDate (big-endian)
        struct[4] = (byte) ((createDate >> 24) & 0xFF);
        struct[5] = (byte) ((createDate >> 16) & 0xFF);
        struct[6] = (byte) ((createDate >> 8) & 0xFF);
        struct[7] = (byte) (createDate & 0xFF);

        // dwByteCount (BIG-ENDIAN per documentation)
        struct[8] = (byte) ((fileSize >> 24) & 0xFF);
        struct[9] = (byte) ((fileSize >> 16) & 0xFF);
        struct[10] = (byte) ((fileSize >> 8) & 0xFF);
        struct[11] = (byte) (fileSize & 0xFF);

        // szText[55] - library + null + subject
        byte[] libraryBytes = library != null ?
            library.getBytes(StandardCharsets.US_ASCII) : new byte[0];
        byte[] subjectBytes = subject != null ?
            subject.getBytes(StandardCharsets.US_ASCII) : new byte[0];

        int pos = 12;
        int maxLibLen = Math.min(libraryBytes.length, 26); // Leave room for null + subject
        System.arraycopy(libraryBytes, 0, struct, pos, maxLibLen);
        pos += maxLibLen;
        struct[pos++] = 0x00; // Null separator

        int maxSubLen = Math.min(subjectBytes.length, TJ_IN_SIZE - pos);
        System.arraycopy(subjectBytes, 0, struct, pos, maxSubLen);
        // Rest is zero-padded by default

        return struct;
    }

    /**
     * Build a complete tj token frame with TJ_IN struct.
     */
    public static byte[] buildTjFrame(int fileType, byte[] fileId, int createDate,
                                       int fileSize, String library, String subject) {
        byte[] struct = buildTjStruct(fileType, fileId, createDate, fileSize, library, subject);
        return buildFrame(TOKEN_TJ_HI, TOKEN_TJ_LO, struct);
    }

    /**
     * Build TF_IN struct (start transfer).
     *
     * <p>TF_IN layout (87 bytes):
     * <pre>
     * [0]      flags (0x20 = progress meter on)
     * [1-3]    size[3] (3-byte file size, LITTLE-ENDIAN)
     * [4]      access (0x00 for MVP)
     * [5]      type (0x00 for MVP)
     * [6-7]    aux_type (0x0000 for MVP)
     * [8]      storage_type (0x00 for MVP)
     * [9-10]   blocks (0x0000 for MVP)
     * [11-14]  time (32-bit modification time)
     * [15-18]  created (32-bit creation time)
     * [19-86]  name[68] (filename + SEP_CHAR + response token)
     * </pre>
     *
     * @param flags transfer flags (use TF_FLAG_PROGRESS_METER for downloads)
     * @param fileSize file size in bytes
     * @param modTime modification time
     * @param createTime creation time
     * @param filename short filename (e.g., "tiny.txt")
     * @return TF_IN struct bytes
     */
    public static byte[] buildTfStruct(int flags, int fileSize, int modTime,
                                        int createTime, String filename) {
        // Default: include SEP_CHAR for downloads (backward compatible)
        return buildTfStruct(flags, fileSize, modTime, createTime, filename, true);
    }

    /**
     * Build TF_IN struct with option to include SEP_CHAR.
     *
     * <p>CRITICAL: Windows clients REQUIRE SEP_CHAR (0x90) in TF_IN.name.
     * The client does: {@code name[FindByte(name, SEP_CHAR) - 1] = 0x00}
     * Without SEP_CHAR, FindByte returns -1, causing a GP fault at name[-2].
     *
     * <p>To prevent filename corruption, we place a NUL byte BEFORE SEP_CHAR:
     * <pre>
     * name[68] layout: [abs_path][0x00][0x90][resp_hi][resp_lo][zero fill...]
     *                             ^     ^
     *                             |     SEP_CHAR (client finds this)
     *                             NUL (client writes here - harmless, already NUL)
     * </pre>
     *
     * @param flags transfer flags
     * @param fileSize file size in bytes
     * @param modTime modification time
     * @param createTime creation time
     * @param filename file path (for uploads: full absolute path from TH_OUT, max 64 chars)
     * @param includeSepChar true to include NUL+SEP_CHAR (required for Windows)
     * @return TF_IN struct bytes
     */
    public static byte[] buildTfStruct(int flags, int fileSize, int modTime,
                                        int createTime, String filename,
                                        boolean includeSepChar) {
        byte[] struct = new byte[TF_IN_SIZE];

        // flags
        struct[0] = (byte) flags;

        // size[3] - LITTLE-ENDIAN (Apple-style)
        struct[1] = (byte) (fileSize & 0xFF);
        struct[2] = (byte) ((fileSize >> 8) & 0xFF);
        struct[3] = (byte) ((fileSize >> 16) & 0xFF);

        // access, type, aux_type, storage_type, blocks - all 0x00 for MVP
        // (bytes 4-10 are already 0)

        // time (modification) - big-endian
        struct[11] = (byte) ((modTime >> 24) & 0xFF);
        struct[12] = (byte) ((modTime >> 16) & 0xFF);
        struct[13] = (byte) ((modTime >> 8) & 0xFF);
        struct[14] = (byte) (modTime & 0xFF);

        // created - big-endian
        struct[15] = (byte) ((createTime >> 24) & 0xFF);
        struct[16] = (byte) ((createTime >> 16) & 0xFF);
        struct[17] = (byte) ((createTime >> 8) & 0xFF);
        struct[18] = (byte) (createTime & 0xFF);

        // name[68] - filename layout depends on includeSepChar
        // Use ISO-8859-1 to preserve Mac OS Roman and other non-ASCII chars
        byte[] nameBytes = filename != null ?
            filename.getBytes(StandardCharsets.ISO_8859_1) : new byte[0];

        // Max name length: need room for NUL + SEP + 2 resp bytes = 4 bytes
        // So maxNameLen = 68 - 4 = 64 when including SEP_CHAR
        int maxNameLen = includeSepChar ?
            Math.min(nameBytes.length, 64) :  // Leave room for NUL + SEP + 2 resp bytes
            Math.min(nameBytes.length, 67);   // Just need room for NUL terminator

        System.arraycopy(nameBytes, 0, struct, 19, maxNameLen);
        // Array is already zero-initialized, so bytes after filename are 0x00

        if (includeSepChar) {
            // Layout: [filename][0x00][0x90][resp_hi][resp_lo]
            // The 0x00 at struct[19 + maxNameLen] is already there from array init
            // Add SEP_CHAR after the NUL - client finds SEP, writes NUL before it (no-op)
            struct[19 + maxNameLen + 1] = TF_SEP_CHAR;
            // Response token bytes at [19 + maxNameLen + 2] and [19 + maxNameLen + 3]
            // Left as 0 for MVP
        }
        // If !includeSepChar: just NUL-terminated filename (for Mac or special cases)

        return struct;
    }

    /**
     * Build a complete tf token frame with TF_IN struct.
     */
    public static byte[] buildTfFrame(int flags, int fileSize, int modTime,
                                       int createTime, String filename) {
        byte[] struct = buildTfStruct(flags, fileSize, modTime, createTime, filename);
        return buildFrame(TOKEN_TF_HI, TOKEN_TF_LO, struct);
    }

    /**
     * Build a complete tf token frame with option to skip SEP_CHAR.
     *
     * @param flags transfer flags
     * @param fileSize file size in bytes
     * @param modTime modification time
     * @param createTime creation time
     * @param filename file path
     * @param includeSepChar true for downloads, false for uploads
     * @return complete P3 DATA frame
     */
    public static byte[] buildTfFrame(int flags, int fileSize, int modTime,
                                       int createTime, String filename,
                                       boolean includeSepChar) {
        byte[] struct = buildTfStruct(flags, fileSize, modTime, createTime, filename, includeSepChar);
        return buildFrame(TOKEN_TF_HI, TOKEN_TF_LO, struct);
    }

    /**
     * Build a data frame (F7 for interior, F9 for final).
     *
     * <p>Data frames carry encoded file bytes directly after the token.
     *
     * @param encodedData escape-encoded file data (from XferEncoder)
     * @param isFinal true for F9 (final chunk), false for F7 (interior)
     * @return complete P3 DATA frame
     */
    public static byte[] buildDataFrame(byte[] encodedData, boolean isFinal) {
        byte tokenHi = isFinal ? TOKEN_F9_HI : TOKEN_F7_HI;
        byte tokenLo = isFinal ? TOKEN_F9_LO : TOKEN_F7_LO;
        return buildFrame(tokenHi, tokenLo, encodedData);
    }

    /**
     * Get the token string from a frame (for debugging).
     *
     * @param frame P3 frame
     * @return token as string (e.g., "tj", "tf", "F7", "F9")
     */
    public static String getTokenString(byte[] frame) {
        if (frame == null || frame.length < ProtocolConstants.MIN_FULL_FRAME_SIZE) {
            return "??";
        }
        char hi = (char) (frame[ProtocolConstants.IDX_TOKEN] & 0xFF);
        char lo = (char) (frame[ProtocolConstants.IDX_TOKEN + 1] & 0xFF);
        return "" + hi + lo;
    }
}
