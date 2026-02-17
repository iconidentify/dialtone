/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;

/**
 * State object for tracking an active XFER file upload (client to server).
 *
 * <p>Tracks the lifecycle of a file upload from initiation through completion.
 * Stored in {@link XferUploadRegistry}, keyed per connection.
 *
 * <p><b>State Machine:</b>
 * <pre>
 * AWAITING_TH_RESPONSE  --[TH_OUT received]-->  AWAITING_TD_RESPONSE
 *        |                                              |
 *        +--[timeout/error]--> FAILED                   |
 *                                                       v
 * AWAITING_TD_RESPONSE  --[TD_OUT received]-->  AWAITING_DATA
 *        |                                              |
 *        +--[timeout/error]--> FAILED                   |
 *                                                       v
 * AWAITING_DATA  --[first xd received]-->  RECEIVING_DATA
 *        |                                              |
 *        +--[timeout]--> FAILED                         |
 *                                                       v
 * RECEIVING_DATA  --[xe received]-->  COMPLETED
 *        |
 *        +--[xK received]--> ABORTED
 *        +--[timeout/size exceeded]--> FAILED
 * </pre>
 *
 * <p>Per xfer_developer_guide.md Appendix A.7: Server sends th to prompt file picker,
 * client responds with TH_OUT containing filename. Server sends td to stat file,
 * client responds with TD_OUT containing size. Server sends tf with 0x80 flag,
 * client immediately begins streaming data (no xG for uploads).
 */
public class XferUploadState {

    /**
     * Upload lifecycle phases.
     */
    public enum Phase {
        /** th sent, waiting for client's TH_OUT with filename */
        AWAITING_TH_RESPONSE,
        /** td sent, waiting for client's TD_OUT with file size */
        AWAITING_TD_RESPONSE,
        /** tf sent with 0x80 flag, waiting for first xd packet */
        AWAITING_DATA,
        /** Actively receiving xd/xb packets */
        RECEIVING_DATA,
        /** Upload finished successfully (xe received, fX sent) */
        COMPLETED,
        /** Upload aborted by client (xK received) */
        ABORTED,
        /** Upload failed (timeout, size exceeded, I/O error) */
        FAILED
    }

    // Identity
    private final String uploadId;
    private final String username;
    private final byte[] responseToken;
    private final long startTimeNanos;
    private final int maxFileSizeBytes;

    // Phase tracking
    private volatile Phase phase;
    private volatile ScheduledFuture<?> timeoutFuture;

    // File metadata (populated after TH_OUT/TD_OUT)
    private volatile String filename;           // Sanitized basename for server storage
    private volatile String originalClientPath; // Full path from TH_OUT for TF frame (Windows needs this)
    private volatile int expectedSize;
    private volatile int receivedBytes;

    // File output
    private volatile Path targetPath;
    private volatile OutputStream outputStream;

    // Abort reason (if applicable)
    private volatile byte abortReasonCode;
    private volatile String failureReason;

    // Frame counter for upload flow control
    // Two modes: tN (experimental) or proactive ACK (stable)
    private volatile int receivedFrameCount;
    private static final int TN_INTERVAL_FRAMES = 6;   // tN: every 6 frames (before 8-frame burst ends)
    private static final int ACK_INTERVAL_FRAMES = 8;  // ACK: every 8 frames (half P3 window)
    private volatile long lastPacketTimeNanos;

    /**
     * Creates a new upload state in AWAITING_TH_RESPONSE phase.
     *
     * @param uploadId unique identifier for this upload
     * @param username display name for logging
     * @param responseToken 2-byte token for correlating TH_OUT/TD_OUT responses
     * @param maxFileSizeBytes maximum allowed file size
     */
    public XferUploadState(String uploadId, String username, byte[] responseToken, int maxFileSizeBytes) {
        this.uploadId = uploadId;
        this.username = username;
        this.responseToken = responseToken;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.startTimeNanos = System.nanoTime();
        this.phase = Phase.AWAITING_TH_RESPONSE;
        this.receivedBytes = 0;
    }

    // ========== Identity Getters ==========

    public String getUploadId() {
        return uploadId;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getResponseToken() {
        return responseToken;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public int getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    // ========== Phase Getters/Setters ==========

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public ScheduledFuture<?> getTimeoutFuture() {
        return timeoutFuture;
    }

    public void setTimeoutFuture(ScheduledFuture<?> future) {
        this.timeoutFuture = future;
    }

    // ========== File Metadata Getters/Setters ==========

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOriginalClientPath() {
        return originalClientPath;
    }

    public void setOriginalClientPath(String originalClientPath) {
        this.originalClientPath = originalClientPath;
    }

    public int getExpectedSize() {
        return expectedSize;
    }

    public void setExpectedSize(int expectedSize) {
        this.expectedSize = expectedSize;
    }

    public int getReceivedBytes() {
        return receivedBytes;
    }

    // ========== File Output Getters/Setters ==========

    public Path getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(Path targetPath) {
        this.targetPath = targetPath;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    // ========== Error State Getters/Setters ==========

    public byte getAbortReasonCode() {
        return abortReasonCode;
    }

    public void setAbortReasonCode(byte abortReasonCode) {
        this.abortReasonCode = abortReasonCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    // ========== Utility Methods ==========

    /**
     * Add to the received bytes counter.
     *
     * @param count number of decoded bytes received
     */
    public void addReceivedBytes(int count) {
        this.receivedBytes += count;
    }

    /**
     * Increment the received frame counter and check if tN should be sent.
     *
     * <p>Per xfer_developer_guide.md: "No XFER-level ACKs are expected from
     * the host for uploads. Use tN opportunistically to prompt more data."
     *
     * <p>tN prompts the client to immediately call xferSendPkt() without
     * waiting for the P3 ACK callback, enabling continuous streaming.
     *
     * @return true if tN should be sent to prompt next upload burst
     */
    public boolean incrementFrameCountAndCheckTn() {
        receivedFrameCount++;
        lastPacketTimeNanos = System.nanoTime();
        return (receivedFrameCount % TN_INTERVAL_FRAMES) == 0;
    }

    /**
     * Increment the received frame counter and check if proactive ACK should be sent.
     *
     * <p>This enables proactive ACK sending to support Windows AOL clients.
     * Returns true every ACK_INTERVAL_FRAMES (8 frames = half P3 window).
     *
     * @return true if proactive ACK should be sent to the client
     */
    public boolean incrementFrameCountAndCheckAck() {
        receivedFrameCount++;
        lastPacketTimeNanos = System.nanoTime();
        return (receivedFrameCount % ACK_INTERVAL_FRAMES) == 0;
    }

    /**
     * Get time since last packet in milliseconds.
     * Useful for stall detection.
     *
     * @return milliseconds since last xd/xb frame received, or 0 if none received yet
     */
    public long getTimeSinceLastPacketMs() {
        if (lastPacketTimeNanos == 0) {
            return 0;
        }
        return (System.nanoTime() - lastPacketTimeNanos) / 1_000_000;
    }

    /**
     * Get the count of received frames.
     *
     * @return number of xd/xb frames received
     */
    public int getReceivedFrameCount() {
        return receivedFrameCount;
    }

    /**
     * Check if file size exceeds the maximum allowed.
     *
     * @return true if receivedBytes or expectedSize exceeds maxFileSizeBytes
     */
    public boolean exceedsMaxSize() {
        return receivedBytes > maxFileSizeBytes || expectedSize > maxFileSizeBytes;
    }

    /**
     * Cancel the timeout future if set.
     *
     * @return true if cancelled, false if already fired or not set
     */
    public boolean cancelTimeout() {
        if (timeoutFuture != null) {
            return timeoutFuture.cancel(false);
        }
        return false;
    }

    /**
     * Get elapsed time since upload started.
     *
     * @return elapsed milliseconds
     */
    public long getElapsedMs() {
        return (System.nanoTime() - startTimeNanos) / 1_000_000;
    }

    /**
     * Check if the upload is in a terminal state.
     *
     * @return true if COMPLETED, ABORTED, or FAILED
     */
    public boolean isTerminal() {
        return phase == Phase.COMPLETED || phase == Phase.ABORTED || phase == Phase.FAILED;
    }

    /**
     * Get progress percentage.
     *
     * @return percentage 0-100, or 0 if expectedSize is unknown
     */
    public int getProgressPercent() {
        if (expectedSize <= 0) {
            return 0;
        }
        return Math.min(100, (receivedBytes * 100) / expectedSize);
    }

    @Override
    public String toString() {
        return String.format(
            "XferUploadState{id=%s, file=%s, expected=%d, received=%d, phase=%s, elapsed=%dms}",
            uploadId, filename, expectedSize, receivedBytes, phase, getElapsedMs());
    }
}
