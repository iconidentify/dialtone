/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.utils.LoggerUtil;

import java.nio.charset.StandardCharsets;

/**
 * Builds XFER protocol frames for file upload (client to server).
 *
 * <p>Creates complete P3 DATA frames for:
 * <ul>
 *   <li>th token (0x7468) - Prompt file picker (TH_IN struct)</li>
 *   <li>td token (0x7464) - Request file stats (TD_IN struct)</li>
 *   <li>tf token (0x7466) - Start upload with 0x80 flag</li>
 *   <li>fX token (0x6658) - Transfer result</li>
 * </ul>
 *
 * <p>Per xfer_developer_guide.md Appendix E.6:
 * <ul>
 *   <li>TH_IN: { token[2], data[117] } - 119 bytes total</li>
 *   <li>TH_OUT: { token[2], count, filename[116] } - from client</li>
 *   <li>TD_IN: { token[2], field, name[65] } - 68 bytes total</li>
 *   <li>TD_OUT: { token[2], rc, size[3], access, type, aux_type, storage_type, blocks, time, created, name[65] }</li>
 *   <li>fX: { rc, message[] } - variable length</li>
 * </ul>
 */
public final class XferUploadFrameBuilder {

    // Token bytes
    public static final byte TOKEN_TH_HI = 0x74;  // 't'
    public static final byte TOKEN_TH_LO = 0x68;  // 'h'
    public static final byte TOKEN_TD_HI = 0x74;  // 't'
    public static final byte TOKEN_TD_LO = 0x64;  // 'd'
    public static final byte TOKEN_FX_HI = 0x66;  // 'f'
    public static final byte TOKEN_FX_LO = 0x58;  // 'X'
    public static final byte TOKEN_TN_HI = 0x74;  // 't'
    public static final byte TOKEN_TN_LO = 0x4E;  // 'N'

    // Struct sizes
    /** TH_IN: token[2] + data[117] = 119 bytes */
    public static final int TH_IN_SIZE = 119;
    /** TD_IN: token[2] + field[1] + name[65] = 68 bytes */
    public static final int TD_IN_SIZE = 68;

    // TF upload flag (from xfer_developer_guide.md A.1)
    public static final byte TF_FLAG_UPLOAD = (byte) 0x80;

    // fX result codes
    public static final byte FX_RESULT_SUCCESS = 0x00;
    public static final byte FX_RESULT_FILE_NOT_FOUND = 0x01;
    public static final byte FX_RESULT_FILE_TOO_LARGE = 0x02;
    public static final byte FX_RESULT_STORAGE_ERROR = 0x03;
    public static final byte FX_RESULT_USER_CANCELLED = 0x04;
    public static final byte FX_RESULT_TIMEOUT = 0x05;
    public static final byte FX_RESULT_PROTOCOL_ERROR = 0x06;

    private XferUploadFrameBuilder() {}

    /**
     * Build TH_IN struct (prompt file picker).
     *
     * <p>TH_IN layout (119 bytes):
     * <pre>
     * [0-1]    token[2] - response correlation token (echoed by client in TH_OUT)
     * [2-118]  data[117] - reserved/unused for MVP
     * </pre>
     *
     * <p>Per xfer_developer_guide.md Appendix A.7: Host sends th with TH_IN
     * containing a 2-byte response token. Client opens file picker via
     * Filer_GetFile and responds with TH_OUT echoing the token.
     *
     * @param responseToken 2-byte correlation token (choose any unique bytes)
     * @return TH_IN struct bytes
     */
    public static byte[] buildThStruct(byte[] responseToken) {
        byte[] struct = new byte[TH_IN_SIZE];

        // Response token (echoed by client in TH_OUT)
        if (responseToken != null && responseToken.length >= 2) {
            struct[0] = responseToken[0];
            struct[1] = responseToken[1];
        }
        // Rest is zero-filled (unused for MVP)

        return struct;
    }

    /**
     * Build a complete th token frame with TH_IN struct.
     *
     * @param responseToken 2-byte correlation token
     * @return complete P3 DATA frame
     */
    public static byte[] buildThFrame(byte[] responseToken) {
        byte[] struct = buildThStruct(responseToken);
        return XferFrameBuilder.buildFrame(TOKEN_TH_HI, TOKEN_TH_LO, struct);
    }

    /**
     * Build TD_IN struct (request file stats).
     *
     * <p>TD_IN layout (68 bytes):
     * <pre>
     * [0-1]    token[2] - response correlation token
     * [2]      field - what to stat (0x01 = size)
     * [3-67]   name[65] - filename to stat
     * </pre>
     *
     * <p>Per xfer_developer_guide.md Appendix A.7: Host sends td with file path.
     * Client returns TD_OUT with size[3] and rc=0x00 if accessible.
     *
     * @param responseToken 2-byte correlation token
     * @param field what to stat (use 0x01 for size)
     * @param filename file path to stat
     * @return TD_IN struct bytes
     */
    public static byte[] buildTdStruct(byte[] responseToken, byte field, String filename) {
        byte[] struct = new byte[TD_IN_SIZE];

        // Response token
        if (responseToken != null && responseToken.length >= 2) {
            struct[0] = responseToken[0];
            struct[1] = responseToken[1];
        }

        // Field
        struct[2] = field;

        // Filename - use ISO-8859-1 to preserve Mac OS Roman and other non-ASCII chars
        if (filename != null) {
            byte[] nameBytes = filename.getBytes(StandardCharsets.ISO_8859_1);
            int copyLen = Math.min(nameBytes.length, 65);
            System.arraycopy(nameBytes, 0, struct, 3, copyLen);
        }

        return struct;
    }

    /**
     * Build a complete td token frame with TD_IN struct.
     *
     * @param responseToken 2-byte correlation token
     * @param field what to stat (use 0x01 for size)
     * @param filename file path to stat
     * @return complete P3 DATA frame
     */
    public static byte[] buildTdFrame(byte[] responseToken, byte field, String filename) {
        byte[] struct = buildTdStruct(responseToken, field, filename);
        return XferFrameBuilder.buildFrame(TOKEN_TD_HI, TOKEN_TD_LO, struct);
    }

    /**
     * Build a complete tf token frame for upload (flags include 0x80).
     *
     * <p>Per xfer_developer_guide.md Appendix A.1: When host sends tf/tt with
     * the upload bit set (0x80), the client prepares to read a local file
     * and stream it to the host. No xG is sent for uploads.
     *
     * <p>CRITICAL: For Windows uploads, filename MUST be the absolute path
     * from TH_OUT (e.g., "C:\aol30\setup.log"). Windows client uses this
     * exact string to open the local file. Do NOT use sanitized basename.
     *
     * <p>SEP_CHAR (0x90) IS REQUIRED for Windows clients. Without it, the client
     * crashes with a GP fault because it unconditionally does:
     * {@code name[FindByte(name, SEP_CHAR) - 1] = 0x00} which becomes name[-2].
     *
     * <p>The underlying XferFrameBuilder now places a NUL before SEP_CHAR, so
     * when the client writes NUL before SEP, it's a no-op (already NUL).
     *
     * @param fileSize file size in bytes (from TD_OUT)
     * @param filename file path to upload (MUST be absolute path from TH_OUT, max 64 chars)
     * @return complete P3 DATA frame
     */
    public static byte[] buildTfUploadFrame(int fileSize, String filename) {
        // Validate path length (max 64 chars to leave room for NUL + SEP + 2 resp bytes)
        if (filename != null && filename.length() > 64) {
            LoggerUtil.warn(String.format(
                "[XferUploadFrameBuilder] Path too long for Windows (%d chars, max 64): %s",
                filename.length(), filename));
        }

        // Use TF struct builder with upload flag and SEP_CHAR
        // SEP_CHAR is REQUIRED - Windows client crashes without it
        // XferFrameBuilder now places NUL before SEP_CHAR to prevent filename corruption
        int flags = XferFrameBuilder.TF_FLAG_PROGRESS_METER | (TF_FLAG_UPLOAD & 0xFF);
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        return XferFrameBuilder.buildTfFrame(flags, fileSize, timestamp, timestamp, filename, true);
    }

    /**
     * Build fX struct (transfer result).
     *
     * <p>fX layout:
     * <pre>
     * [0]      rc - result code (0x00 = success)
     * [1+]     message - NUL-terminated result message
     * </pre>
     *
     * <p>Per xfer_developer_guide.md Appendix A.6: fX_TOKEN is interpreted
     * by the client as upload completion status when (xferFlags & 0x80) != 0.
     * Client closes meter and shows message.
     *
     * @param resultCode result code (use FX_RESULT_* constants)
     * @param message human-readable result message (can be null)
     * @return fX struct bytes
     */
    public static byte[] buildFxStruct(byte resultCode, String message) {
        byte[] msgBytes = message != null ?
            message.getBytes(StandardCharsets.US_ASCII) : new byte[0];

        // rc + message + NUL terminator
        byte[] struct = new byte[1 + msgBytes.length + 1];
        struct[0] = resultCode;
        if (msgBytes.length > 0) {
            System.arraycopy(msgBytes, 0, struct, 1, msgBytes.length);
        }
        struct[struct.length - 1] = 0x00; // NUL terminator

        return struct;
    }

    /**
     * Build a complete fX token frame with result.
     *
     * @param resultCode result code (use FX_RESULT_* constants)
     * @param message human-readable result message (can be null)
     * @return complete P3 DATA frame
     */
    public static byte[] buildFxFrame(byte resultCode, String message) {
        byte[] struct = buildFxStruct(resultCode, message);
        return XferFrameBuilder.buildFrame(TOKEN_FX_HI, TOKEN_FX_LO, struct);
    }

    /**
     * Build a success fX frame.
     *
     * @param message success message (e.g., "Upload complete")
     * @return complete P3 DATA frame
     */
    public static byte[] buildFxSuccessFrame(String message) {
        return buildFxFrame(FX_RESULT_SUCCESS, message);
    }

    /**
     * Build an error fX frame.
     *
     * @param resultCode error code (non-zero)
     * @param message error description
     * @return complete P3 DATA frame
     */
    public static byte[] buildFxErrorFrame(byte resultCode, String message) {
        return buildFxFrame(resultCode, message);
    }

    /**
     * Build tN token frame to prompt client to send next upload burst.
     *
     * <p>Per xfer_developer_guide.md Section A.2 and Appendix F.1:
     * tN has NO payload - just the token bytes. When client receives tN,
     * it immediately calls xferSendPkt() without waiting for P3 ACK callback.
     *
     * <p>From XFER/xfer.c lines 275-277:
     * <pre>
     * case tN_TOKEN:
     *     xferSendPkt();
     *     break;
     * </pre>
     *
     * <p>This is the proper XFER-layer mechanism for upload flow control,
     * bypassing the normal P3 ACK callback wait cycle.
     *
     * @return complete P3 DATA frame with tN token and no payload
     */
    public static byte[] buildTnFrame() {
        // tN has no payload - just token bytes
        return XferFrameBuilder.buildFrame(TOKEN_TN_HI, TOKEN_TN_LO, null);
    }
}
