/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.utils.LoggerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Utility for wrapping AtomForge P3 chunk payloads into AOL DATA frames and enqueuing them via the pacer.
 */
public final class P3ChunkEnqueuer {

    private P3ChunkEnqueuer() {}

    private static String logPrefix(String username) {
        return username != null ? "[" + username + "][ChunkEnqueuer] " : "[ChunkEnqueuer] ";
    }

    /**
     * Get Stream ID size in bytes based on token (per AOL protocol spec from atomizer.c).
     *
     * <p>The token case determines how many bytes of Stream ID are on the wire:</p>
     * <table>
     *   <tr><th>Token</th><th>Hex</th><th>Wire SID Size</th></tr>
     *   <tr><td>AT</td><td>0x4154</td><td>2 bytes</td></tr>
     *   <tr><td>At</td><td>0x4174</td><td>3 bytes</td></tr>
     *   <tr><td>at</td><td>0x6174</td><td>4 bytes</td></tr>
     *   <tr><td>aT</td><td>0x6154</td><td>0 bytes (client generates)</td></tr>
     * </table>
     *
     * @param tokenHi first byte of token
     * @param tokenLo second byte of token
     * @return size of Stream ID in bytes (0, 2, 3, or 4)
     */
    private static int getStreamIdSizeForToken(byte tokenHi, byte tokenLo) {
        boolean firstUpper = tokenHi >= 'A' && tokenHi <= 'Z';
        boolean secondUpper = tokenLo >= 'A' && tokenLo <= 'Z';

        if (firstUpper && secondUpper) return 2;       // "AT" -> 2 bytes
        if (!firstUpper && !secondUpper) return 4;     // "at" -> 4 bytes
        if (firstUpper && !secondUpper) return 3;      // "At" -> 3 bytes
        if (!firstUpper && secondUpper) return 0;      // "aT" -> 0 bytes
        return 2; // default
    }

    /**
     * Wrap AtomForge chunk payload in a minimal AOL DATA frame (headers will be restamped by downstream logic).
     *
     * <p>Atomforge HTTP payload format:</p>
     * <pre>
     * [Token (2 bytes)] [StreamID (variable)] [FDO data]
     * </pre>
     *
     * <p>IMPORTANT: This method keeps the embedded Stream ID in the frame payload.
     * For basic (non-extended) frames, the stream ID stays embedded in the FDO data portion.
     * Only wrapP3PayloadWithStreamId() strips it (because it moves it to frame header bytes 10-11).</p>
     */
    public static byte[] wrapP3Payload(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return new byte[0];
        }

        byte tokenHi = payload[0];
        byte tokenLo = payload[1];

        // For basic frames, we keep everything after the token (including embedded stream ID)
        // The stream ID stays embedded in the FDO data portion of the frame
        int fdoDataLength = payload.length - 2;  // Skip only token

        boolean needsTerminator = fdoDataLength <= 0 || payload[payload.length - 1] != 0x0D;
        int totalLength = ProtocolConstants.MIN_FULL_FRAME_SIZE + fdoDataLength + (needsTerminator ? 1 : 0);
        byte[] frame = new byte[totalLength];

        frame[ProtocolConstants.IDX_MAGIC] = (byte) ProtocolConstants.AOL_FRAME_MAGIC;
        // CRC (IDX_CRC_HI/LO) left as 0; restamp will recompute
        // Length bytes (IDX_LEN_HI/LO) will be recomputed by restamp
        frame[ProtocolConstants.IDX_TYPE] = (byte) PacketType.DATA.getValue();
        frame[ProtocolConstants.IDX_TOKEN] = tokenHi;
        frame[ProtocolConstants.IDX_TOKEN + 1] = tokenLo;

        if (fdoDataLength > 0) {
            System.arraycopy(payload, 2, frame, ProtocolConstants.MIN_FULL_FRAME_SIZE, fdoDataLength);
        }

        if (needsTerminator) {
            frame[totalLength - 1] = 0x0D;
        }

        return frame;
    }

    /**
     * Wrap AtomForge chunk payload in an EXTENDED AOL DATA frame with Stream ID.
     * Used for DOD responses and other P3 frames that require Stream ID preservation.
     *
     * <p>Atomforge payload structure (variable-length stream ID based on token case):</p>
     * <pre>
     * [Token (2 bytes)] [StreamID (0-4 bytes per token case)] [FDO data]
     * </pre>
     *
     * <p>Stream ID size per token case:</p>
     * <ul>
     *   <li>"AT" (both uppercase): 2 bytes</li>
     *   <li>"At" (upper first, lower second): 3 bytes</li>
     *   <li>"at" (both lowercase): 4 bytes</li>
     *   <li>"aT" (lower first, upper second): 0 bytes</li>
     * </ul>
     *
     * <p>We skip the embedded Stream ID since we're adding our own at frame bytes 10-11.</p>
     *
     * @param payload AtomForge chunk payload (token + embedded streamId + FDO data)
     * @param streamId Stream ID to embed at bytes 10-11 (big-endian)
     * @return Complete P3 frame with 12-byte extended header
     */
    public static byte[] wrapP3PayloadWithStreamId(byte[] payload, int streamId) {
        if (payload == null || payload.length < 2) {
            return new byte[0];
        }

        // Extract token from payload
        byte tokenHi = payload[0];
        byte tokenLo = payload[1];

        // Determine embedded stream ID size from token case
        int embeddedStreamIdSize = getStreamIdSizeForToken(tokenHi, tokenLo);
        int fdoDataStart = 2 + embeddedStreamIdSize;  // Skip token + embedded streamId
        int fdoDataLength = payload.length - fdoDataStart;

        if (fdoDataLength < 0) {
            return new byte[0];  // Malformed payload
        }

        // Create EXTENDED frame with 12-byte header (includes Stream ID at 10-11)
        int headerSize = 12;
        boolean needsTerminator = fdoDataLength <= 0 || payload[payload.length - 1] != 0x0D;
        int totalLength = headerSize + fdoDataLength + (needsTerminator ? 1 : 0);
        byte[] frame = new byte[totalLength];

        // Basic header (0-9)
        frame[ProtocolConstants.IDX_MAGIC] = (byte) ProtocolConstants.AOL_FRAME_MAGIC; // 0x5A
        // CRC (1-2) left as 0; restamp will recompute
        // Length (3-4) left as 0; restamp will recompute
        // TX/RX (5-6) left as 0; restamp will set
        frame[ProtocolConstants.IDX_TYPE] = (byte) PacketType.DATA.getValue(); // 0x20
        frame[ProtocolConstants.IDX_TOKEN] = tokenHi;         // 8
        frame[ProtocolConstants.IDX_TOKEN + 1] = tokenLo;     // 9

        // Stream ID (10-11) - BIG ENDIAN
        frame[10] = (byte) ((streamId >> 8) & 0xFF);
        frame[11] = (byte) (streamId & 0xFF);

        // Copy FDO data starting at offset 12, SKIPPING Atomforge's embedded Stream ID
        if (fdoDataLength > 0) {
            System.arraycopy(payload, fdoDataStart, frame, headerSize, fdoDataLength);
        }

        if (needsTerminator) {
            frame[totalLength - 1] = 0x0D;
        }

        return frame;
    }

    /**
     * Enqueue a list of P3 chunks as individual frames and trigger a paced drain.
     * Respects maxBurstFrames to prevent P3 window violations on strict clients (Mac).
     */
    public static void enqueue(ChannelHandlerContext ctx,
                               Pacer pacer,
                               List<FdoChunk> chunks,
                               String labelPrefix,
                               int maxBurstFrames,
                               String username) {
        // Warn if large batch - may require multiple drain cycles
        if (chunks.size() > maxBurstFrames) {
            LoggerUtil.info(logPrefix(username) + String.format(
                "Large chunk list (%d) exceeds maxBurstFrames (%d) for %s - will pace across drains",
                chunks.size(), maxBurstFrames, labelPrefix));
        }

        int index = 0;
        int enqueuedThisBatch = 0;
        for (FdoChunk chunk : chunks) {
            byte[] payload = chunk.getBinaryData();
            byte[] framed = wrapP3Payload(payload);
            ByteBuf buf = ctx.alloc().buffer(framed.length);
            try {
                buf.writeBytes(framed);
                String label = String.format("%s_%02d", labelPrefix, index++);
                pacer.enqueue(buf, label);
                enqueuedThisBatch++;

                // Trigger drain after each batch to prevent window overflow (Mac client fix)
                if (enqueuedThisBatch >= maxBurstFrames && index < chunks.size()) {
                    LoggerUtil.debug(logPrefix(username) + String.format(
                        "Reached maxBurstFrames (%d), draining before next batch", maxBurstFrames));
                    pacer.drainLimited(ctx, maxBurstFrames);
                    enqueuedThisBatch = 0;
                }
            } finally {
                buf.release();
            }
        }
    }

    /**
     * Smart enqueuer that respects per-chunk Stream IDs for multi-stream responses.
     * Each chunk can carry its own Stream ID metadata. If a chunk has a Stream ID,
     * it uses extended frame format; otherwise it uses basic frame format.
     *
     * This enables responses like DOD where different chunks need different Stream IDs
     * (e.g., dod_response.fdo.txt uses incoming streamId, idb_response.fdo.txt uses 0x2111).
     *
     * Respects maxBurstFrames to prevent P3 window violations on strict clients (Mac).
     *
     * @param ctx Channel context
     * @param pacer Frame pacer
     * @param chunks FDO chunks with optional per-chunk Stream ID metadata
     * @param labelPrefix Debug label prefix
     * @param maxBurstFrames Maximum frames per burst
     * @param username Username for logging context
     */
    public static void enqueueChunksWithMixedStreamIds(ChannelHandlerContext ctx,
                                                       Pacer pacer,
                                                       List<FdoChunk> chunks,
                                                       String labelPrefix,
                                                       int maxBurstFrames,
                                                       String username) {
        // Warn about large chunk lists that will require multiple drain cycles
        if (chunks.size() > maxBurstFrames) {
            LoggerUtil.info(logPrefix(username) + String.format(
                "Large chunk list (%d) exceeds maxBurstFrames (%d) for %s - will pace across drains",
                chunks.size(), maxBurstFrames, labelPrefix));
        }
        LoggerUtil.debug(logPrefix(username) + "Enqueueing " + chunks.size() + " chunks with prefix " + labelPrefix);

        int index = 0;
        int enqueuedThisBatch = 0;
        for (FdoChunk chunk : chunks) {
            byte[] payload = chunk.getBinaryData();
            byte[] framed;
            String label;

            if (chunk.hasStreamId()) {
                // Chunk has Stream ID - use extended frame format
                int streamId = chunk.getStreamId();
                int finalIndex = index;
                String finalUsername = username;
                LoggerUtil.debug(() -> String.format(
                    logPrefix(finalUsername) + "Chunk %d has streamId 0x%04X, size=%d bytes",
                    finalIndex, streamId, payload.length));
                framed = wrapP3PayloadWithStreamId(payload, streamId);
                label = String.format("%s_%02d_SID%04X", labelPrefix, index++, streamId);
            } else {
                // No Stream ID - use basic frame format
                int finalIndex = index;
                String finalUsername = username;
                LoggerUtil.debug(() -> String.format(
                    logPrefix(finalUsername) + "Chunk %d has no streamId, size=%d bytes",
                    finalIndex, payload.length));
                framed = wrapP3Payload(payload);
                label = String.format("%s_%02d", labelPrefix, index++);
            }

            ByteBuf buf = ctx.alloc().buffer(framed.length);
            try {
                buf.writeBytes(framed);
                pacer.enqueue(buf, label);
                enqueuedThisBatch++;

                // Trigger drain after each batch to prevent window overflow (Mac client fix)
                if (enqueuedThisBatch >= maxBurstFrames && index < chunks.size()) {
                    LoggerUtil.debug(logPrefix(username) + String.format(
                        "Reached maxBurstFrames (%d), draining before next batch", maxBurstFrames));
                    pacer.drainLimited(ctx, maxBurstFrames);
                    enqueuedThisBatch = 0;
                }
            } finally {
                buf.release();
            }
        }
    }
}


