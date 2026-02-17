/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe accumulator for multi-frame protocol streams identified by Stream ID.
 *
 * <p>Some protocol tokens (e.g., Aa chat, Kk keyword) can arrive in multiple frames
 * across the network. Each frame carries a Stream ID, and the final frame contains
 * a {@code uni_end_stream} marker. This class provides a reusable mechanism to
 * accumulate frames until the stream is complete.
 *
 * <p><b>Usage Pattern:</b>
 * <pre>
 * MultiFrameStreamAccumulator accumulator = new MultiFrameStreamAccumulator();
 *
 * // On each frame arrival:
 * if (isEndOfStream(frame)) {
 *     List&lt;byte[]&gt; allFrames = accumulator.getAndClear(streamId);
 *     if (allFrames != null) {
 *         // Process multi-frame stream
 *         processStream(allFrames);
 *     } else {
 *         // Single-frame stream
 *         processSingleFrame(frame);
 *     }
 * } else {
 *     // Accumulate intermediate frame
 *     accumulator.accumulate(streamId, frame);
 * }
 * </pre>
 *
 * @see com.dialtone.protocol.StatefulClientHandler
 */
public class MultiFrameStreamAccumulator {

    /**
     * Storage for frames grouped by Stream ID.
     * Each Stream ID maps to a list of frames in arrival order.
     */
    private final Map<Integer, List<byte[]>> pendingStreams = new ConcurrentHashMap<>();

    /**
     * Accumulates a frame for the given Stream ID.
     *
     * <p>The frame is copied to prevent external modifications from affecting stored data.
     *
     * @param streamId the Stream ID from the P3 frame header (bytes 10-11)
     * @param frame    the complete frame data
     */
    public void accumulate(int streamId, byte[] frame) {
        if (frame == null || frame.length == 0) {
            throw new IllegalArgumentException("Cannot accumulate null or empty frame");
        }

        // Copy frame to prevent external modifications
        byte[] frameCopy = Arrays.copyOf(frame, frame.length);

        pendingStreams.computeIfAbsent(streamId, k -> new ArrayList<>()).add(frameCopy);
    }

    /**
     * Retrieves accumulated frames for a Stream ID without removing them.
     *
     * @param streamId the Stream ID to query
     * @return list of accumulated frames, or null if no frames exist for this Stream ID
     */
    public List<byte[]> get(int streamId) {
        return pendingStreams.get(streamId);
    }

    /**
     * Checks if any frames are accumulated for the given Stream ID.
     *
     * @param streamId the Stream ID to check
     * @return true if frames exist for this Stream ID, false otherwise
     */
    public boolean hasFrames(int streamId) {
        List<byte[]> frames = pendingStreams.get(streamId);
        return frames != null && !frames.isEmpty();
    }

    /**
     * Retrieves accumulated frames for a Stream ID and removes them from storage.
     *
     * <p>This is typically called when the final frame (with uni_end_stream) arrives.
     *
     * @param streamId the Stream ID to retrieve and clear
     * @return list of accumulated frames, or null if no frames exist for this Stream ID
     */
    public List<byte[]> getAndClear(int streamId) {
        return pendingStreams.remove(streamId);
    }

    /**
     * Clears all accumulated frames for a Stream ID.
     *
     * @param streamId the Stream ID to clear
     */
    public void clear(int streamId) {
        pendingStreams.remove(streamId);
    }

    /**
     * Clears all pending streams.
     *
     * <p>This should be called when the connection closes to prevent memory leaks.
     */
    public void clearAll() {
        pendingStreams.clear();
    }

    /**
     * Returns the number of pending streams currently accumulating frames.
     *
     * @return number of unique Stream IDs with accumulated frames
     */
    public int size() {
        return pendingStreams.size();
    }

    /**
     * Checks if the accumulator is empty (no pending streams).
     *
     * @return true if no streams are pending, false otherwise
     */
    public boolean isEmpty() {
        return pendingStreams.isEmpty();
    }
}
