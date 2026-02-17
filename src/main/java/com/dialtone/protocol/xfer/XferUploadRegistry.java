/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Per-connection registry for active XFER file uploads (client to server).
 *
 * <p>Manages the lifecycle of file uploads from initiation through completion.
 * Each connection has its own registry instance to track pending uploads.
 *
 * <p><b>Thread Safety:</b> All public methods are synchronized for safe access
 * from Netty event loop and timeout scheduler threads.
 *
 * <p><b>Design Decision:</b> For MVP, only one upload per connection is allowed.
 * Additional upload attempts while one is pending will be rejected.
 *
 * <p>Per xfer_developer_guide.md: Unlike downloads (which send xG), uploads
 * do not have an acknowledgment phase. The client immediately begins streaming
 * data after receiving tf with 0x80 flag.
 */
public class XferUploadRegistry {

    private final String username;
    private volatile XferUploadState activeUpload;

    /**
     * Creates a new registry for the specified user.
     *
     * @param username display name for logging (may be "unknown" initially)
     */
    public XferUploadRegistry(String username) {
        this.username = username != null ? username : "unknown";
    }

    /**
     * Register a new upload awaiting TH_OUT response.
     *
     * @param state the upload state to register
     * @throws IllegalStateException if an upload is already in progress
     */
    public synchronized void registerPendingUpload(XferUploadState state) {
        if (activeUpload != null && !activeUpload.isTerminal()) {
            throw new IllegalStateException(String.format(
                "Upload already in progress for %s (existing: %s in phase %s, new: %s)",
                username, activeUpload.getUploadId(), activeUpload.getPhase(), state.getUploadId()));
        }
        this.activeUpload = state;
        LoggerUtil.info(String.format(
            "[%s][XferUploadRegistry] Registered upload %s awaiting TH_OUT",
            username, state.getUploadId()));
    }

    /**
     * Find an upload by its response token.
     * Used to correlate TH_OUT and TD_OUT responses with pending uploads.
     *
     * @param token the 2-byte response token from client response
     * @return the matching upload, or null if not found
     */
    public synchronized XferUploadState findByResponseToken(byte[] token) {
        if (activeUpload == null || token == null || token.length < 2) {
            return null;
        }
        if (Arrays.equals(activeUpload.getResponseToken(), token)) {
            return activeUpload;
        }
        // Token mismatch - could be stale response or protocol error
        LoggerUtil.debug(String.format(
            "[%s][XferUploadRegistry] Token mismatch: expected [%02X %02X], got [%02X %02X]",
            username,
            activeUpload.getResponseToken()[0] & 0xFF, activeUpload.getResponseToken()[1] & 0xFF,
            token[0] & 0xFF, token[1] & 0xFF));
        return null;
    }

    /**
     * Get the current active upload (any phase).
     *
     * @return the active upload, or null if none
     */
    public synchronized XferUploadState getActiveUpload() {
        return activeUpload;
    }

    /**
     * Check if an upload is currently in progress (non-terminal phase).
     *
     * @return true if an upload is active
     */
    public synchronized boolean hasActiveUpload() {
        return activeUpload != null && !activeUpload.isTerminal();
    }

    /**
     * Abandon any existing non-terminal upload to make way for a new one.
     * Called when client initiates a new upload, implying the previous
     * upload window was closed/abandoned.
     *
     * @return true if an upload was abandoned, false if none existed
     */
    public synchronized boolean abandonStaleUpload() {
        if (activeUpload != null && !activeUpload.isTerminal()) {
            LoggerUtil.info(String.format(
                "[%s][XferUploadRegistry] Abandoning stale upload %s (phase: %s, received: %d bytes) - new upload requested",
                username, activeUpload.getUploadId(), activeUpload.getPhase(),
                activeUpload.getReceivedBytes()));
            activeUpload.cancelTimeout();
            activeUpload.setPhase(XferUploadState.Phase.ABORTED);
            activeUpload.setFailureReason("Abandoned: new upload initiated by client");
            cleanupPartialFile();
            activeUpload = null;
            return true;
        }
        return false;
    }

    /**
     * Mark the current upload as completed.
     */
    public synchronized void markCompleted() {
        if (activeUpload != null) {
            activeUpload.cancelTimeout();
            activeUpload.setPhase(XferUploadState.Phase.COMPLETED);
            LoggerUtil.info(String.format(
                "[%s][XferUploadRegistry] Upload %s COMPLETED: %s (%d bytes in %dms)",
                username, activeUpload.getUploadId(), activeUpload.getFilename(),
                activeUpload.getReceivedBytes(), activeUpload.getElapsedMs()));
        }
    }

    /**
     * Mark the current upload as failed.
     *
     * @param reason description of failure cause
     */
    public synchronized void markFailed(String reason) {
        if (activeUpload != null) {
            activeUpload.cancelTimeout();
            activeUpload.setPhase(XferUploadState.Phase.FAILED);
            activeUpload.setFailureReason(reason);
            LoggerUtil.error(String.format(
                "[%s][XferUploadRegistry] Upload %s FAILED: %s (received %d bytes in %dms)",
                username, activeUpload.getUploadId(), reason,
                activeUpload.getReceivedBytes(), activeUpload.getElapsedMs()));
            cleanupPartialFile();
        }
    }

    /**
     * Mark the current upload as aborted by client (xK received).
     *
     * @param reasonCode the abort reason code from xK packet
     */
    public synchronized void markAborted(int reasonCode) {
        if (activeUpload != null) {
            activeUpload.cancelTimeout();
            activeUpload.setPhase(XferUploadState.Phase.ABORTED);
            activeUpload.setAbortReasonCode((byte) reasonCode);
            LoggerUtil.warn(String.format(
                "[%s][XferUploadRegistry] Upload %s ABORTED by client: reason=0x%02X (received %d bytes in %dms)",
                username, activeUpload.getUploadId(), reasonCode & 0xFF,
                activeUpload.getReceivedBytes(), activeUpload.getElapsedMs()));
            cleanupPartialFile();
        }
    }

    /**
     * Handle timeout - called by scheduled timeout task.
     * Fails the upload if still in a non-terminal waiting phase.
     */
    public synchronized void handleTimeout() {
        if (activeUpload != null && !activeUpload.isTerminal()) {
            XferUploadState.Phase phase = activeUpload.getPhase();
            String phaseDesc = switch (phase) {
                case AWAITING_TH_RESPONSE -> "TH_OUT (file selection)";
                case AWAITING_TD_RESPONSE -> "TD_OUT (file stats)";
                case AWAITING_DATA -> "first data packet";
                case RECEIVING_DATA -> "more data (stalled)";
                default -> phase.toString();
            };
            activeUpload.setPhase(XferUploadState.Phase.FAILED);
            activeUpload.setFailureReason("Timeout waiting for " + phaseDesc);
            LoggerUtil.error(String.format(
                "[%s][XferUploadRegistry] Upload %s TIMED OUT waiting for %s (waited %dms)",
                username, activeUpload.getUploadId(), phaseDesc, activeUpload.getElapsedMs()));
            cleanupPartialFile();
        }
    }

    /**
     * Clear the active upload (for cleanup after completion/failure).
     */
    public synchronized void clearActiveUpload() {
        if (activeUpload != null) {
            LoggerUtil.debug(String.format(
                "[%s][XferUploadRegistry] Clearing upload %s (phase: %s)",
                username, activeUpload.getUploadId(), activeUpload.getPhase()));
            activeUpload = null;
        }
    }

    /**
     * Clean up on connection close.
     * Cancels any pending timeout, closes streams, and deletes partial files.
     */
    public synchronized void close() {
        if (activeUpload != null) {
            activeUpload.cancelTimeout();
            if (!activeUpload.isTerminal()) {
                LoggerUtil.warn(String.format(
                    "[%s][XferUploadRegistry] Abandoning upload %s on disconnect (phase: %s, received: %d bytes, elapsed: %dms)",
                    username, activeUpload.getUploadId(), activeUpload.getPhase(),
                    activeUpload.getReceivedBytes(), activeUpload.getElapsedMs()));
                cleanupPartialFile();
            }
            activeUpload = null;
        }
    }

    /**
     * Update the username (called after authentication).
     *
     * @param newUsername the authenticated username
     * @return a new registry with the updated username
     */
    public XferUploadRegistry withUsername(String newUsername) {
        XferUploadRegistry newRegistry = new XferUploadRegistry(newUsername);
        newRegistry.activeUpload = this.activeUpload;
        return newRegistry;
    }

    /**
     * Clean up partial file and close stream.
     * Called on failure, abort, or disconnect.
     */
    private void cleanupPartialFile() {
        if (activeUpload == null) {
            return;
        }

        // Close output stream
        if (activeUpload.getOutputStream() != null) {
            try {
                activeUpload.getOutputStream().close();
            } catch (IOException e) {
                LoggerUtil.debug(String.format(
                    "[%s][XferUploadRegistry] Error closing output stream: %s",
                    username, e.getMessage()));
            }
        }

        // Delete partial file
        if (activeUpload.getTargetPath() != null) {
            try {
                boolean deleted = Files.deleteIfExists(activeUpload.getTargetPath());
                if (deleted) {
                    LoggerUtil.debug(String.format(
                        "[%s][XferUploadRegistry] Deleted partial file: %s",
                        username, activeUpload.getTargetPath()));
                }
            } catch (IOException e) {
                LoggerUtil.warn(String.format(
                    "[%s][XferUploadRegistry] Failed to delete partial file %s: %s",
                    username, activeUpload.getTargetPath(), e.getMessage()));
            }
        }
    }

    /**
     * Get registry username for logging.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }
}
