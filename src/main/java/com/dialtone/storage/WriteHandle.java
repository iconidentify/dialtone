/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Handle returned from write operations.
 *
 * <p>Contains the output stream for writing content and the actual filename
 * that was used (which may differ from the requested filename due to
 * collision handling or sanitization).
 *
 * @param outputStream stream for writing content
 * @param actualFilename the actual filename used (may differ from requested)
 * @param path the filesystem path where file will be written (may be null for non-filesystem storage)
 */
public record WriteHandle(
    OutputStream outputStream,
    String actualFilename,
    Path path
) {
    /**
     * Create handle without path.
     */
    public WriteHandle(OutputStream outputStream, String actualFilename) {
        this(outputStream, actualFilename, null);
    }
}
