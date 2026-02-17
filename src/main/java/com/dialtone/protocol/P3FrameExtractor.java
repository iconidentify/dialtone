/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.aol.core.FrameCodec;
import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.utils.LoggerUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Extracts Stream ID and metadata from P3 protocol frames.
 *
 * P3 Frame Structure Analysis:
 * - Byte 0: 0x5A (sync byte)
 * - Byte 1: Type (e.g., 0x20 for DATA)
 * - Bytes 2-3: Token (e.g., "fh", "AT", "at")
 * - Bytes 4-5: Flags/Sequence (big-endian)
 * - Bytes 6-7: Payload length (big-endian)
 *
 * For extended P3 frames with Stream ID:
 * - Bytes 8-9: Additional header bytes (often 0x00)
 * - Bytes 10-11: Stream ID (big-endian) - This is what we need!
 * - Bytes 12+: FDO payload starts
 *
 * The Stream ID appears to be at offset 10-11 in frames that have it.
 * For DOD requests, the client sends Stream ID 0x2100 (8448 decimal).
 */
public class P3FrameExtractor {

    /**
     * Extract P3 frame metadata including Stream ID from an fh frame.
     *
     * @param frame Raw P3 frame bytes
     * @return P3FrameMetadata with extracted Stream ID and other metadata
     * @throws IllegalArgumentException if frame is invalid
     */
    public static P3FrameMetadata extractMetadata(byte[] frame) {
        if (frame == null || frame.length < ProtocolConstants.MIN_FRAME_SIZE) {
            throw new IllegalArgumentException("Frame too short or null");
        }

        // Verify it's a valid P3 frame
        if ((frame[0] & 0xFF) != ProtocolConstants.AOL_FRAME_MAGIC) {
            throw new IllegalArgumentException("Invalid P3 frame magic byte");
        }

        // Extract basic frame info
        int type = frame[1] & 0xFF;
        String token = FrameCodec.extractTokenAscii(frame);
        int flags = u16(frame, 4);
        int payloadLen = u16(frame, 6);

        // For DOD frames, the Stream ID is typically at a fixed offset
        // Let's look for it in multiple possible locations
        int streamId = extractStreamId(frame, payloadLen);

        // Extract FDO payload (starts after header)
        byte[] fdoPayload = null;
        int payloadStart = 8; // Default start
        int extendedHeaderSize = 0;

        // If we found a stream ID at offset 10-11, payload starts at 12
        if (frame.length >= 12 && hasStreamIdAtOffset10(frame)) {
            payloadStart = 12;
            extendedHeaderSize = 4; // bytes 8-11 are extended header
        }

        // CRITICAL: payloadLen is measured from offset 8, but when Stream ID is present,
        // the actual FDO payload starts at offset 12. So we need to subtract the extended header size.
        int fdoPayloadLen = payloadLen - extendedHeaderSize;

        // Create final copies for lambda capture
        final int finalExtendedHeaderSize = extendedHeaderSize;
        final int finalFdoPayloadLen = fdoPayloadLen;
        final int finalPayloadStart = payloadStart;

        if (fdoPayloadLen > 0 && frame.length >= payloadStart + fdoPayloadLen) {
            fdoPayload = Arrays.copyOfRange(frame, payloadStart, payloadStart + fdoPayloadLen);
            LoggerUtil.debug(() -> String.format(
                "Extracted FDO payload: payloadLen=%d, extendedHeaderSize=%d, fdoPayloadLen=%d, payloadStart=%d",
                payloadLen, finalExtendedHeaderSize, finalFdoPayloadLen, finalPayloadStart
            ));
        } else if (fdoPayloadLen <= 0) {
            LoggerUtil.warn(String.format(
                "Invalid FDO payload length: payloadLen=%d, extendedHeaderSize=%d, fdoPayloadLen=%d",
                payloadLen, finalExtendedHeaderSize, finalFdoPayloadLen
            ));
        } else {
            LoggerUtil.warn(String.format(
                "Frame too short for FDO payload: frameLen=%d, need=%d",
                frame.length, payloadStart + fdoPayloadLen
            ));
        }

        LoggerUtil.debug(() -> String.format(
            "Extracted P3 metadata: token=%s, type=0x%02X, streamId=0x%04X(%d), payloadLen=%d",
            token, type, streamId, streamId, payloadLen
        ));

        return new P3FrameMetadata(streamId, token, flags, flags & 0xFF, fdoPayload);
    }

    /**
     * Extract Stream ID from various possible locations in the frame.
     * Different frame types may store the Stream ID at different offsets.
     */
    private static int extractStreamId(byte[] frame, int payloadLen) {
        // Method 1: Check at offset 10-11 (common for extended P3 frames)
        if (frame.length >= 12) {
            int streamId = u16(frame, 10);
            // Sanity check: Stream IDs are typically non-zero and reasonable values
            if (streamId > 0 && streamId < 0xFFFF) {
                LoggerUtil.debug(() -> String.format("Found Stream ID at offset 10: 0x%04X", streamId));
                return streamId;
            }
        }

        // Method 2: Check first 2 bytes of payload (offset 8-9)
        if (frame.length >= 10) {
            int streamId = u16(frame, 8);
            if (streamId > 0 && streamId < 0xFFFF) {
                LoggerUtil.debug(() -> String.format("Found Stream ID at offset 8: 0x%04X", streamId));
                return streamId;
            }
        }


        LoggerUtil.debug(() -> "No Stream ID found in frame");
        return 0;
    }

    /**
     * Check if there's likely a Stream ID at offset 10-11.
     */
    private static boolean hasStreamIdAtOffset10(byte[] frame) {
        if (frame.length < 12) return false;

        int value = u16(frame, 10);
        // Stream IDs are typically non-zero and in reasonable range
        return value > 0 && value <= 0xFFFF;
    }

    /**
     * Create a DOD response frame with the same Stream ID as the request.
     *
     * @param requestStreamId Stream ID from the incoming request
     * @param dodResponsePayload Compiled DOD response FDO payload
     * @param token Token for the response (e.g., "AT", "at")
     * @return Complete P3 frame ready to send
     */
    public static byte[] buildDodResponseFrame(int requestStreamId, byte[] dodResponsePayload, String token) {
        // P3 frame with Stream ID embedded
        int headerSize = 12; // Extended header with Stream ID
        int totalSize = headerSize + dodResponsePayload.length;
        byte[] frame = new byte[totalSize];

        // Basic P3 header
        frame[0] = (byte) ProtocolConstants.AOL_FRAME_MAGIC; // 0x5A
        frame[1] = (byte) 0x20; // DATA type

        // Token (2 bytes)
        if (token != null && token.length() >= 2) {
            frame[2] = (byte) token.charAt(0);
            frame[3] = (byte) token.charAt(1);
        } else {
            frame[2] = 'A';
            frame[3] = 'T';
        }

        // Flags/sequence
        frame[4] = 0x00;
        frame[5] = 0x00;

        // Payload length (big-endian)
        frame[6] = (byte) ((dodResponsePayload.length >> 8) & 0xFF);
        frame[7] = (byte) (dodResponsePayload.length & 0xFF);

        // Extended header bytes
        frame[8] = 0x00;
        frame[9] = 0x00;

        // Stream ID (big-endian)
        frame[10] = (byte) ((requestStreamId >> 8) & 0xFF);
        frame[11] = (byte) (requestStreamId & 0xFF);

        // Copy payload
        System.arraycopy(dodResponsePayload, 0, frame, headerSize, dodResponsePayload.length);

        LoggerUtil.info(String.format(
            "Built DOD response frame: token=%s, streamId=0x%04X(%d), payloadSize=%d",
            token, requestStreamId, requestStreamId, dodResponsePayload.length
        ));

        return frame;
    }

    /**
     * Read unsigned 16-bit big-endian value.
     */
    private static int u16(byte[] data, int offset) {
        if (offset + 1 >= data.length) return 0;
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * Debug helper: dump frame bytes in hex format.
     */
    public static String dumpFrameHeader(byte[] frame, int maxBytes) {
        if (frame == null) return "null";

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(frame.length, maxBytes);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", frame[i] & 0xFF));
            if (i == 11) sb.append(" |"); // Mark end of extended header
        }
        if (frame.length > limit) {
            sb.append(" ...");
        }
        return sb.toString();
    }
}