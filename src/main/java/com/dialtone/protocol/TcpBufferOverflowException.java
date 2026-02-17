/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

/**
 * Exception thrown when TCP buffer limits are exceeded.
 * This indicates either a potential DOS attack or corrupt data
 * causing infinite accumulation attempts.
 */
public class TcpBufferOverflowException extends Exception {
    private final int bufferSize;
    private final int attemptCount;

    /**
     * Creates exception with diagnostic information.
     *
     * @param message Error description
     * @param bufferSize Size that exceeded limits
     * @param attemptCount Number of accumulation attempts made
     */
    public TcpBufferOverflowException(String message, int bufferSize, int attemptCount) {
        super(message + String.format(" (size: %d bytes, attempts: %d)", bufferSize, attemptCount));
        this.bufferSize = bufferSize;
        this.attemptCount = attemptCount;
    }

    /**
     * Gets the buffer size that caused the overflow.
     *
     * @return Buffer size in bytes
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Gets the number of accumulation attempts made before overflow.
     *
     * @return Accumulation attempt count
     */
    public int getAttemptCount() {
        return attemptCount;
    }
}