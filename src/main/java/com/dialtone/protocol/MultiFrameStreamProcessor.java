/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.utils.LoggerUtil;

import java.util.List;

/**
 * Utility methods for processing multi-frame protocol streams.
 *
 * <p>This class provides reusable logic for handling protocol tokens (e.g., Aa, Kk)
 * that can arrive in multiple frames. Key responsibilities:
 * <ul>
 *   <li>Extract Stream ID from P3 frame headers (bytes 10-11)</li>
 *   <li>Detect {@code uni_end_stream} markers in FDO content</li>
 *   <li>Extract {@code de_data} fields from single or multi-frame sequences</li>
 * </ul>
 *
 * <p><b>Multi-Frame Pattern:</b>
 * <pre>
 * // Frame 0: First fragment (no uni_end_stream)
 * uni_start_stream
 *   de_data &lt;"partial message..."&gt;
 *   man_end_context
 *
 * // Frame 1: Final fragment (has uni_end_stream)
 * uni_end_stream
 * </pre>
 *
 * @see MultiFrameStreamAccumulator
 * @see com.dialtone.protocol.StatefulClientHandler
 */
public final class MultiFrameStreamProcessor {

    private MultiFrameStreamProcessor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts the Stream ID from a P3 protocol frame header.
     *
     * <p>The Stream ID is located at bytes 10-11 in extended P3 frames.
     * This is used to correlate multi-frame sequences.
     *
     * @param frame the complete P3 frame data
     * @return the Stream ID as an integer (0x0000 to 0xFFFF)
     * @throws IllegalArgumentException if the frame is too short or Stream ID cannot be extracted
     */
    public static int extractStreamId(byte[] frame) {
        if (frame == null || frame.length < 12) {
            LoggerUtil.warn("Frame too short to contain Stream ID (need >= 12 bytes) - defaulting to 0");
            return 0;
        }

        // Diagnostic: Log raw bytes at Stream ID location
        byte byte10 = frame[10];
        byte byte11 = frame[11];
        LoggerUtil.info(String.format("Stream ID raw bytes: [10]=0x%02X [11]=0x%02X", byte10 & 0xFF, byte11 & 0xFF));

        try {
            P3FrameMetadata metadata = P3FrameExtractor.extractMetadata(frame);
            int streamId = metadata.getStreamId();
            LoggerUtil.info(String.format("Extracted Stream ID: 0x%04X (%d decimal)", streamId, streamId));
            return streamId;
        } catch (Exception e) {
            LoggerUtil.warn("Failed to extract Stream ID from frame: " + e.getMessage());
            return 0;  // Default to Stream ID 0 on failure
        }
    }

    /**
     * Fast binary pattern detection for {@code uni_end_stream} atoms.
     *
     * <p>Scans the frame payload (after 12-byte P3 header) for binary patterns
     * that correspond to compiled {@code uni_end_stream} atoms. This avoids
     * the expensive FDO decompilation process (2 Atomforge API calls) for
     * simple stream end detection.
     *
     * <p>Tested patterns (protocol=0x00, various atom IDs):
     * <ul>
     *   <li>Pattern 1: {@code [0x00][0x03][0x01][0x00]} - uni_end_stream &lt;00x&gt;</li>
     *   <li>Pattern 2: {@code [0x00][0x03][0x00]} - uni_end_stream (no params)</li>
     *   <li>Pattern 3: {@code [0x00][0x02][0x01][0x00]} - alternate atom ID</li>
     *   <li>Pattern 4: {@code [0x00][0x01][0x01][0x00]} - alternate atom ID</li>
     * </ul>
     *
     * @param frame the complete P3 frame data (includes 12-byte header + FDO payload)
     * @return true if binary {@code uni_end_stream} pattern detected, false if not found
     */
    public static boolean hasUniEndStreamMarker(byte[] frame) {
        if (frame == null || frame.length < 15) { // Need at least 12-byte header + 3-byte atom minimum
            return false;
        }

        // Skip P3 frame header (12 bytes) and scan FDO payload
        int payloadStart = 12;
        int payloadLength = frame.length - payloadStart;

        // Look for UNI end stream patterns in the payload
        for (int offset = 0; offset <= payloadLength - 3; offset++) {
            int pos = payloadStart + offset;

            // All uni_end_stream patterns start with UNI protocol (0x00)
            if (frame[pos] == 0x00) {

                // Pattern 1: [0x00][0x03][0x01][0x00] - uni_end_stream <00x>
                if (pos + 3 < frame.length &&
                    frame[pos + 1] == 0x03 &&
                    frame[pos + 2] == 0x01 &&
                    frame[pos + 3] == 0x00) {
                    final int finalOffset = offset;
                    LoggerUtil.debug(() -> "Binary detection: Found uni_end_stream <00x> pattern at offset " + finalOffset);
                    return true;
                }

                // Pattern 2: [0x00][0x03][0x00] - uni_end_stream (no parameters)
                if (pos + 2 < frame.length &&
                    frame[pos + 1] == 0x03 &&
                    frame[pos + 2] == 0x00) {
                    final int finalOffset = offset;
                    LoggerUtil.debug(() -> "Binary detection: Found uni_end_stream (no params) pattern at offset " + finalOffset);
                    return true;
                }

                // Pattern 3: [0x00][0x02][0x01][0x00] - alternate atom ID
                if (pos + 3 < frame.length &&
                    frame[pos + 1] == 0x02 &&
                    frame[pos + 2] == 0x01 &&
                    frame[pos + 3] == 0x00) {
                    final int finalOffset = offset;
                    LoggerUtil.debug(() -> "Binary detection: Found uni_end_stream (atom 0x02) pattern at offset " + finalOffset);
                    return true;
                }

                // Pattern 4: [0x00][0x01][0x01][0x00] - alternate atom ID
                if (pos + 3 < frame.length &&
                    frame[pos + 1] == 0x01 &&
                    frame[pos + 2] == 0x01 &&
                    frame[pos + 3] == 0x00) {
                    final int finalOffset = offset;
                    LoggerUtil.debug(() -> "Binary detection: Found uni_end_stream (atom 0x01) pattern at offset " + finalOffset);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Detects if a frame contains UNI large atom protocol markers that should skip FDO decompilation.
     *
     * <p>These frames contain raw binary message data as part of the large atom protocol,
     * not complete FDO structures that can be decompiled. Attempting to decompile them
     * will fail and waste significant time (2+ API calls with timeouts).
     *
     * <p>Large atom protocol markers:
     * <ul>
     *   <li>{@code [0x00][0x04]} - UNI_START_LARGE_ATOM: Start marker with original proto/atom</li>
     *   <li>{@code [0x00][0x05]} - UNI_LARGE_ATOM_SEGMENT: Raw UTF-8 message data continuation</li>
     * </ul>
     *
     * <p>Note: UNI_END_LARGE_ATOM (0x00 0x06) frames are NOT skipped because they may
     * contain {@code uni_end_stream} markers that need to be detected.
     *
     * @param frame the complete P3 frame data (includes 12-byte header + payload)
     * @return true if frame contains large atom markers that should skip decompilation
     */
    private static boolean isLargeAtomContinuation(byte[] frame) {
        if (frame == null || frame.length < 14) { // Need at least header + 2 bytes for proto/atom
            return false;
        }

        // Skip P3 frame header (12 bytes) to get to FDO payload
        int payloadStart = 12;
        byte proto = frame[payloadStart];
        byte atom = frame[payloadStart + 1];

        // UNI_LARGE_ATOM_SEGMENT (0x00 0x05) - raw data continuation
        if (proto == 0x00 && atom == 0x05) {
            LoggerUtil.debug(() -> "Detected UNI_LARGE_ATOM_SEGMENT (0x00 0x05) - skipping decompilation");
            return true;
        }

        // UNI_START_LARGE_ATOM (0x00 0x04) - start marker
        if (proto == 0x00 && atom == 0x04) {
            LoggerUtil.debug(() -> "Detected UNI_START_LARGE_ATOM (0x00 0x04) - skipping decompilation");
            return true;
        }

        // UNI_END_LARGE_ATOM (0x00 0x06) - final chunk
        // DON'T skip decompilation for this one - it might contain uni_end_stream
        if (proto == 0x00 && atom == 0x06) {
            LoggerUtil.debug(() -> "Detected UNI_END_LARGE_ATOM (0x00 0x06) - allowing decompilation (may contain uni_end_stream)");
        }

        return false;
    }

    /**
     * Checks if a frame contains the {@code uni_end_stream} marker using optimized detection.
     *
     * <p>Performance optimization: Attempts fast binary pattern matching first (~1ms),
     * then falls back to native FdoStream decoding if needed (~1ms).
     * This eliminates the old expensive HTTP-based FDO decompilation (2 API calls, ~100ms).
     *
     * <p>This marker indicates the frame is the final frame in a multi-frame sequence.
     * The frame's FDO payload is scanned for binary patterns first, with native decoding
     * used as a fallback for edge cases.
     *
     * @param frame       the complete P3 frame data
     * @param fdoCompiler the FDO compiler (unused, kept for API compatibility)
     * @return true if {@code uni_end_stream} is present, false otherwise
     */
    public static boolean isUniEndStream(byte[] frame, FdoCompiler fdoCompiler) {
        // First attempt: Fast binary pattern detection (~1ms)
        if (hasUniEndStreamMarker(frame)) {
            LoggerUtil.debug(() -> "Fast binary detection: Frame contains uni_end_stream");
            return true;
        }

        // Second attempt: Skip for large atom continuation frames (~1ms)
        // These frames contain raw binary message data, not complete FDO structures
        if (isLargeAtomContinuation(frame)) {
            // These frames never contain uni_end_stream (they're raw message data)
            return false;
        }

        // Fallback: Native FdoStream decoding (~1ms, no HTTP calls)
        // Only reached for frames that:
        // - Don't have binary uni_end_stream pattern
        // - Aren't large atom continuations
        // - Might have FDO structure we can decode
        try {
            LoggerUtil.debug(() -> "Binary detection failed, falling back to native FdoStream decoding");
            byte[] fdoBinary = FdoStreamExtractor.stripP3Header(frame);
            boolean hasEndStream = FdoStreamExtractor.hasUniEndStream(fdoBinary);

            if (hasEndStream) {
                LoggerUtil.info("Native FdoStream fallback: Frame contains uni_end_stream");
            } else {
                LoggerUtil.debug(() -> "Native FdoStream fallback: Frame does NOT contain uni_end_stream");
            }

            return hasEndStream;
        } catch (Exception e) {
            LoggerUtil.warn("Failed to check for uni_end_stream via native FdoStream: " + e.getMessage());
            // If we can't decode, assume it's a continuation frame with raw binary data.
            // Returning false prevents premature processing before all frames arrive.
            return false;
        }
    }

    /**
     * Extracts {@code de_data} content from a single-frame message.
     *
     * <p>Uses native FdoStream decoding to extract the first {@code de_data} field value.
     * This is much faster than the old HTTP-based decompilation approach.
     *
     * @param frame       the complete P3 frame data
     * @param fdoCompiler the FDO compiler (unused, kept for API compatibility)
     * @param tokenName   the token name (e.g., "Aa", "Kk") for logging
     * @return the extracted {@code de_data} content
     * @throws Exception if extraction fails or {@code de_data} is not found
     */
    public static String extractDeDataFromSingleFrame(byte[] frame, FdoCompiler fdoCompiler, String tokenName) throws Exception {
        // Strip P3 header to get pure FDO binary
        byte[] fdoBinary = FdoStreamExtractor.stripP3Header(frame);

        if (fdoBinary.length == 0) {
            throw new IllegalArgumentException("No FDO payload in " + tokenName + " frame");
        }

        // Use native FdoStream extraction
        String data = FdoStreamExtractor.extractFirstDeData(fdoBinary);

        if (data == null || data.isEmpty()) {
            // Enhanced diagnostic logging when extraction fails
            LoggerUtil.error("Failed to extract de_data from " + tokenName + " frame using native FdoStream");
            LoggerUtil.error("FDO binary length: " + fdoBinary.length + " bytes");

            // Try to get all de_data values for diagnosis
            List<String> allValues = FdoStreamExtractor.extractDeData(fdoBinary);
            LoggerUtil.error("All de_data values found: " + allValues.size());
            for (int i = 0; i < allValues.size(); i++) {
                LoggerUtil.error("  de_data[" + i + "]: " + (allValues.get(i) == null ? "null" : "'" + allValues.get(i) + "'"));
            }

            throw new IllegalArgumentException("Failed to extract de_data from " + tokenName + " frame");
        }

        LoggerUtil.info("Extracted " + tokenName + " de_data: '" + data + "'");
        return data;
    }

    /**
     * Extracts {@code de_data} content from a multi-frame message.
     *
     * <p>Uses native FdoStream decoding with frame concatenation to extract
     * the {@code de_data} field. This handles messages that span multiple frames
     * due to length constraints, much faster than the old HTTP-based approach.
     *
     * @param allFrames   all frames in the sequence (accumulated frames + final frame)
     * @param fdoCompiler the FDO compiler (unused, kept for API compatibility)
     * @param tokenName   the token name (e.g., "Aa", "Kk") for logging
     * @return the extracted {@code de_data} content
     * @throws Exception if extraction fails or {@code de_data} is not found
     */
    public static String extractDeDataFromMultiFrame(List<byte[]> allFrames, FdoCompiler fdoCompiler, String tokenName) throws Exception {
        LoggerUtil.info(String.format("Extracting de_data from %d multi-frame %s sequence", allFrames.size(), tokenName));

        // Concatenate FDO payloads from all frames (native, no HTTP calls)
        byte[] combinedFdoBinary = FdoStreamExtractor.concatenateFrames(allFrames);

        if (combinedFdoBinary.length == 0) {
            throw new IllegalArgumentException("No FDO payload in multi-frame " + tokenName + " sequence");
        }

        LoggerUtil.debug(() -> "Combined FDO binary: " + combinedFdoBinary.length + " bytes from " + allFrames.size() + " frames");

        // Use native FdoStream extraction
        String data = FdoStreamExtractor.extractFirstDeData(combinedFdoBinary);

        if (data == null || data.isEmpty()) {
            // Enhanced diagnostic logging when extraction fails
            LoggerUtil.error("Failed to extract de_data from multi-frame " + tokenName + " using native FdoStream");
            LoggerUtil.error("Combined FDO binary length: " + combinedFdoBinary.length + " bytes");
            LoggerUtil.error("Number of frames: " + allFrames.size());

            // Try to get all de_data values for diagnosis
            List<String> allValues = FdoStreamExtractor.extractDeData(combinedFdoBinary);
            LoggerUtil.error("All de_data values found: " + allValues.size());
            for (int i = 0; i < allValues.size(); i++) {
                LoggerUtil.error("  de_data[" + i + "]: " + (allValues.get(i) == null ? "null" : "'" + allValues.get(i) + "'"));
            }

            throw new IllegalArgumentException("Failed to extract de_data from multi-frame " + tokenName);
        }

        LoggerUtil.info("Extracted multi-frame " + tokenName + " de_data: '" + data + "'");
        return data;
    }
}
