/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import java.util.concurrent.ScheduledFuture;

/**
 * Immutable state object for tracking an active XFER file transfer.
 *
 * <p>Tracks the lifecycle of a file transfer from initiation through completion.
 * Stored in {@link XferTransferRegistry}, keyed per connection.
 *
 * <p><b>State Machine:</b>
 * <pre>
 * AWAITING_XG  --[xG received]-->  SENDING_DATA  --[F9 sent]-->  COMPLETED
 *      |                                 |
 *      +--[timeout/error]-->  FAILED  <--+
 * </pre>
 */
public class XferTransferState {

    /**
     * Transfer lifecycle phases.
     */
    public enum Phase {
        /** tf sent, waiting for client's xG acknowledgment */
        AWAITING_XG,
        /** xG received, sending F7/F8/F9 data frames */
        SENDING_DATA,
        /** Transfer finished successfully */
        COMPLETED,
        /** Transfer failed (timeout, error, disconnect) */
        FAILED,
        /** Transfer cancelled by client */
        CANCELLED
    }

    private final String transferId;
    private final String filename;
    private final int fileSize;
    private final byte[] fileId;
    private final byte[] encodedData;
    private final int timestamp;
    private final long startTimeNanos;
    private final String username;
    private volatile Phase phase;
    private volatile ScheduledFuture<?> timeoutFuture;

    /**
     * Creates a new transfer state in AWAITING_XG phase.
     *
     * @param transferId unique identifier for this transfer
     * @param filename short filename
     * @param fileSize raw file size in bytes
     * @param fileId 3-byte file ID for tj/tf correlation
     * @param encodedData pre-encoded data (escape encoded)
     * @param timestamp Unix timestamp for file metadata
     * @param username display name for logging
     */
    public XferTransferState(String transferId, String filename, int fileSize,
                             byte[] fileId, byte[] encodedData, int timestamp,
                             String username) {
        this.transferId = transferId;
        this.filename = filename;
        this.fileSize = fileSize;
        this.fileId = fileId;
        this.encodedData = encodedData;
        this.timestamp = timestamp;
        this.username = username;
        this.startTimeNanos = System.nanoTime();
        this.phase = Phase.AWAITING_XG;
    }

    // ========== Getters ==========

    public String getTransferId() {
        return transferId;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getFileId() {
        return fileId;
    }

    public byte[] getEncodedData() {
        return encodedData;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public String getUsername() {
        return username;
    }

    public Phase getPhase() {
        return phase;
    }

    public ScheduledFuture<?> getTimeoutFuture() {
        return timeoutFuture;
    }

    // ========== Setters ==========

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public void setTimeoutFuture(ScheduledFuture<?> future) {
        this.timeoutFuture = future;
    }

    // ========== Utility Methods ==========

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
     * Get elapsed time since transfer started.
     *
     * @return elapsed milliseconds
     */
    public long getElapsedMs() {
        return (System.nanoTime() - startTimeNanos) / 1_000_000;
    }

    /**
     * Get raw file size in bytes.
     *
     * @return file size
     */
    public int getFileSize() {
        return fileSize;
    }

    @Override
    public String toString() {
        return String.format("XferTransferState{id=%s, file=%s, size=%d, phase=%s, elapsed=%dms}",
                transferId, filename, getFileSize(), phase, getElapsedMs());
    }
}
