/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

/**
 * Represents a broadcast message that has been deferred due to an active DOD transfer.
 *
 * <p>When a connection is processing a DOD (Download on Demand) fh request, all broadcast
 * messages (CA, chat, IMs) are queued rather than sent immediately. This prevents client
 * disconnects caused by unexpected tokens arriving during DOD data transfers.
 *
 * @param data The raw frame bytes to send
 * @param label The pacer label for logging
 * @param timestamp When the broadcast was deferred (System.currentTimeMillis())
 */
public record DeferredBroadcast(byte[] data, String label, long timestamp) {

    /**
     * Get the age of this deferred broadcast in milliseconds.
     *
     * @return Age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }
}
