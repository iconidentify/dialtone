/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.spi.impl;

import com.atomforge.fdo.FdoCompiler;
import com.atomforge.fdo.FdoException;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.spi.FdoCompilationException;
import com.dialtone.fdo.spi.FdoCompilationService;
import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Native Java implementation of FdoCompilationService.
 *
 * <p>Uses the atomforge-fdo Java library directly for compilation.
 * This provides cross-platform FDO compilation without HTTP overhead.</p>
 *
 * <p>Key differences from HTTP backend:</p>
 * <ul>
 *   <li>Chunking is done locally using the FrameConsumer callback API</li>
 *   <li>Large atoms use UNI continuation protocol (atoms 4, 5, 6)</li>
 *   <li>Token bytes are prepended to each frame payload by this implementation</li>
 * </ul>
 */
public class NativeFdoCompilationService implements FdoCompilationService {

    private static final String BACKEND_NAME = "java";

    /**
     * Default wire frame length (as seen in wiretap).
     * This is the total frame size on the wire including header and terminator.
     */
    private static final int DEFAULT_WIRE_FRAME_LENGTH = 194;

    /**
     * Offset to convert wire frame length to chunk payload size.
     *
     * <p>Wire frame structure for extended frames (AT token):</p>
     * <pre>
     * Wire frame = header (12) + fdo_data + terminator (1)
     * Chunk payload = token (2) + streamId (2 for AT) + fdo_data
     * </pre>
     *
     * <p>Therefore:</p>
     * <pre>
     * chunk_payload = wire_frame - 12 - 1 + 4 = wire_frame - 9
     * </pre>
     */
    private static final int WIRE_TO_PAYLOAD_OFFSET = 9;

    /**
     * Maximum FDO data size per chunk payload.
     *
     * <p>Payload structure: [Token (2)] [StreamID (2-4)] [FDO data]</p>
     * <p>For AT token (2-byte streamId): payload - 4 = max FDO data bytes</p>
     *
     * <p>The actual max FDO data per frame is calculated as:</p>
     * <pre>
     * maxFdoDataPerFrame = p3MaxChunkPayload - headerOverhead
     * </pre>
     * <p>Where headerOverhead = token (2) + streamId (0-4 based on token case)</p>
     */
    private final int p3MaxChunkPayload;

    private final FdoCompiler nativeCompiler;

    /**
     * Create native compilation service with default wire frame length (194 bytes).
     */
    public NativeFdoCompilationService() {
        this(DEFAULT_WIRE_FRAME_LENGTH);
    }

    /**
     * Create native compilation service with configurable wire frame length.
     *
     * @param wireFrameLength maximum frame length on the wire (as seen in wiretap)
     */
    public NativeFdoCompilationService(int wireFrameLength) {
        this.nativeCompiler = FdoCompiler.create();
        this.p3MaxChunkPayload = wireFrameLength - WIRE_TO_PAYLOAD_OFFSET;
        LoggerUtil.info(String.format(
            "[%s] Configured with wire frame length=%d, chunk payload=%d",
            BACKEND_NAME, wireFrameLength, this.p3MaxChunkPayload));
    }

    /**
     * Create native compilation service with custom compiler.
     * Useful for testing.
     *
     * @param compiler pre-configured FdoCompiler
     */
    public NativeFdoCompilationService(FdoCompiler compiler) {
        this.nativeCompiler = compiler;
        this.p3MaxChunkPayload = DEFAULT_WIRE_FRAME_LENGTH - WIRE_TO_PAYLOAD_OFFSET;
    }

    @Override
    public byte[] compile(String fdoSource) throws FdoCompilationException {
        try {
            return nativeCompiler.compile(fdoSource);
        } catch (FdoException e) {
            throw new FdoCompilationException(
                formatNativeError(e),
                BACKEND_NAME,
                e
            );
        }
    }

    @Override
    public List<FdoChunk> compileToChunks(String fdoSource, String token, int streamId)
            throws FdoCompilationException {
        try {
            List<FdoChunk> chunks = new ArrayList<>();
            byte[] tokenBytes = token.getBytes(StandardCharsets.US_ASCII);
            int streamIdSize = getStreamIdSizeForToken(token);
            int headerOverhead = tokenBytes.length + streamIdSize;
            int maxFdoDataPerFrame = p3MaxChunkPayload - headerOverhead;

            nativeCompiler.compileToFrames(fdoSource, maxFdoDataPerFrame, (frameData, frameIndex, isLastFrame) -> {
                byte[] payload = new byte[headerOverhead + frameData.length];
                System.arraycopy(tokenBytes, 0, payload, 0, tokenBytes.length);
                for (int i = 0; i < streamIdSize; i++) {
                    payload[tokenBytes.length + i] = (byte) ((streamId >> (i * 8)) & 0xFF);
                }

                // FDO data at bytes 2+streamIdSize onwards
                System.arraycopy(frameData, 0, payload, headerOverhead, frameData.length);

                // Create FdoChunk with hex representation (matches HTTP API format)
                FdoChunk chunk = new FdoChunk(frameIndex, payload.length, bytesToHex(payload));

                // Tag with Stream ID metadata if non-zero (for P3ChunkEnqueuer routing)
                if (streamId != 0) {
                    chunk.setStreamId(streamId);
                }

                chunks.add(chunk);
            });

            LoggerUtil.debug(String.format(
                "[%s] Compiled FDO to %d chunks, streamId=0x%04X, streamIdSize=%d",
                BACKEND_NAME, chunks.size(), streamId, streamIdSize
            ));

            return chunks;
        } catch (FdoException e) {
            throw new FdoCompilationException(
                formatNativeError(e),
                BACKEND_NAME,
                e
            );
        }
    }

    @Override
    public String getBackendName() {
        return BACKEND_NAME;
    }

    @Override
    public boolean isHealthy() {
        // Simple health check: try to compile minimal FDO
        try {
            compile("uni_start_stream <00x>\nuni_end_stream\n");
            return true;
        } catch (FdoCompilationException e) {
            LoggerUtil.warn(String.format("[%s] Health check failed: %s", BACKEND_NAME, e.getMessage()));
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        // Native compiler has no resources to release
    }

    /**
     * Format native FdoException for consistent error messages.
     */
    private String formatNativeError(FdoException e) {
        StringBuilder sb = new StringBuilder();
        sb.append("FDO compilation failed: ").append(e.getMessage());

        if (e.hasLocation()) {
            sb.append(" at line ").append(e.getLine());
            if (e.getColumn() > 0) {
                sb.append(", column ").append(e.getColumn());
            }
        }

        if (e.getCode() != null) {
            sb.append(" (code: ").append(e.getCode()).append(")");
        }

        return sb.toString();
    }

    /**
     * Convert byte array to hex string (uppercase).
     * Matches format used by HTTP API.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    /**
     * Get the Stream ID size in bytes based on token.
     *
     * <p>Per AOL protocol specification (from atomizer.c), the Stream ID wire size varies by token case:</p>
     * <ul>
     *   <li>"AT" (both uppercase): 2 bytes</li>
     *   <li>"At" (upper first, lower second): 3 bytes</li>
     *   <li>"at" (both lowercase): 4 bytes</li>
     *   <li>"aT" (lower first, upper second): 0 bytes (client generates internally)</li>
     * </ul>
     *
     * @param token 2-character token string
     * @return size of Stream ID in bytes (0, 2, 3, or 4)
     */
    private int getStreamIdSizeForToken(String token) {
        if (token == null || token.length() != 2) {
            return 2; // Default
        }

        char first = token.charAt(0);
        char second = token.charAt(1);

        if (Character.isUpperCase(first) && Character.isUpperCase(second)) {
            return 2;  // "AT", "DD", etc. -> 2 bytes
        } else if (Character.isLowerCase(first) && Character.isLowerCase(second)) {
            return 4;  // "at", "dd", etc. -> 4 bytes
        } else if (Character.isUpperCase(first) && Character.isLowerCase(second)) {
            return 3;  // "At", "Dd", etc. -> 3 bytes
        } else if (Character.isLowerCase(first) && Character.isUpperCase(second)) {
            return 0;  // "aT", "dD", etc. -> 0 bytes (client generates internally)
        }
        return 2; // Default for other cases
    }
}
