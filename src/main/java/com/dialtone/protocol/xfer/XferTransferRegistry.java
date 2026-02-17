/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.utils.LoggerUtil;

/**
 * Per-connection registry for active XFER file transfers.
 *
 * <p>Manages the lifecycle of file transfers waiting for xG acknowledgment.
 * Each connection has its own registry instance to track pending transfers.
 *
 * <p><b>Thread Safety:</b> All public methods are synchronized for safe access
 * from Netty event loop and timeout scheduler threads.
 *
 * <p><b>Design Decision:</b> For MVP, only one transfer per connection is allowed.
 * Additional transfer attempts while one is pending will be rejected.
 * Future enhancement: Map&lt;String, XferTransferState&gt; for concurrent transfers.
 */
public class XferTransferRegistry {

    private final String username;
    private volatile XferTransferState activeTransfer;

    /**
     * Creates a new registry for the specified user.
     *
     * @param username display name for logging (may be "unknown" initially)
     */
    public XferTransferRegistry(String username) {
        this.username = username != null ? username : "unknown";
    }

    /**
     * Register a new transfer awaiting xG acknowledgment.
     *
     * @param state the transfer state to register
     * @throws IllegalStateException if a transfer is already pending
     */
    public synchronized void registerPendingTransfer(XferTransferState state) {
        if (activeTransfer != null &&
            activeTransfer.getPhase() == XferTransferState.Phase.AWAITING_XG) {
            throw new IllegalStateException(String.format(
                "Transfer already pending for %s (existing: %s, new: %s)",
                username, activeTransfer.getTransferId(), state.getTransferId()));
        }
        this.activeTransfer = state;
        LoggerUtil.info(String.format(
            "[%s][XferRegistry] Registered transfer %s awaiting xG (file: %s, size: %d bytes)",
            username, state.getTransferId(), state.getFilename(), state.getFileSize()));
    }

    /**
     * Called when xG token is received from client.
     *
     * @return the pending transfer, or null if none found or wrong phase
     */
    public synchronized XferTransferState onXgReceived() {
        if (activeTransfer == null) {
            LoggerUtil.warn(String.format(
                "[%s][XferRegistry] xG received but no transfer registered", username));
            return null;
        }

        if (activeTransfer.getPhase() != XferTransferState.Phase.AWAITING_XG) {
            LoggerUtil.warn(String.format(
                "[%s][XferRegistry] xG received but transfer in wrong phase: %s",
                username, activeTransfer.getPhase()));
            return null;
        }

        LoggerUtil.info(String.format(
            "[%s][XferRegistry] xG received for transfer %s (waited %dms)",
            username, activeTransfer.getTransferId(), activeTransfer.getElapsedMs()));

        return activeTransfer;
    }

    /**
     * Check if a transfer is currently awaiting xG.
     *
     * @return true if a transfer is in AWAITING_XG phase
     */
    public boolean hasTransferAwaitingXg() {
        return activeTransfer != null &&
               activeTransfer.getPhase() == XferTransferState.Phase.AWAITING_XG;
    }

    /**
     * Get the current active transfer (any phase).
     *
     * @return the active transfer, or null if none
     */
    public XferTransferState getActiveTransfer() {
        return activeTransfer;
    }

    /**
     * Mark the current transfer as completed.
     */
    public synchronized void markCompleted() {
        if (activeTransfer != null) {
            activeTransfer.setPhase(XferTransferState.Phase.COMPLETED);
            LoggerUtil.info(String.format(
                "[%s][XferRegistry] Transfer %s completed (total time: %dms)",
                username, activeTransfer.getTransferId(), activeTransfer.getElapsedMs()));
        }
    }

    /**
     * Mark the current transfer as failed.
     *
     * @param reason description of failure cause
     */
    public synchronized void markFailed(String reason) {
        if (activeTransfer != null) {
            activeTransfer.setPhase(XferTransferState.Phase.FAILED);
            LoggerUtil.error(String.format(
                "[%s][XferRegistry] Transfer %s FAILED: %s (elapsed: %dms)",
                username, activeTransfer.getTransferId(), reason, activeTransfer.getElapsedMs()));
        }
    }

    /**
     * Handle timeout - called by scheduled timeout task.
     * Only fails the transfer if still in AWAITING_XG phase.
     */
    public synchronized void handleTimeout() {
        if (activeTransfer != null &&
            activeTransfer.getPhase() == XferTransferState.Phase.AWAITING_XG) {
            activeTransfer.setPhase(XferTransferState.Phase.FAILED);
            LoggerUtil.error(String.format(
                "[%s][XferRegistry] Transfer %s TIMED OUT waiting for xG (waited %dms)",
                username, activeTransfer.getTransferId(), activeTransfer.getElapsedMs()));
            activeTransfer = null;
        }
    }

    /**
     * Clear the active transfer (for cleanup after completion/failure).
     */
    public synchronized void clearActiveTransfer() {
        if (activeTransfer != null) {
            LoggerUtil.debug(String.format(
                "[%s][XferRegistry] Clearing transfer %s (phase: %s)",
                username, activeTransfer.getTransferId(), activeTransfer.getPhase()));
            activeTransfer = null;
        }
    }

    /**
     * Clean up on connection close.
     * Cancels any pending timeout and abandons in-progress transfers.
     */
    public synchronized void close() {
        if (activeTransfer != null) {
            activeTransfer.cancelTimeout();
            if (activeTransfer.getPhase() == XferTransferState.Phase.AWAITING_XG ||
                activeTransfer.getPhase() == XferTransferState.Phase.SENDING_DATA) {
                LoggerUtil.warn(String.format(
                    "[%s][XferRegistry] Abandoning transfer %s on disconnect (phase: %s, elapsed: %dms)",
                    username, activeTransfer.getTransferId(), activeTransfer.getPhase(),
                    activeTransfer.getElapsedMs()));
            }
            activeTransfer = null;
        }
    }

    /**
     * Update the username (called after authentication).
     *
     * @return a new registry with the updated username
     */
    public XferTransferRegistry withUsername(String newUsername) {
        XferTransferRegistry newRegistry = new XferTransferRegistry(newUsername);
        newRegistry.activeTransfer = this.activeTransfer;
        return newRegistry;
    }
}
