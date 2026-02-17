/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable file metadata record.
 *
 * @param filename the filename (sanitized)
 * @param sizeBytes file size in bytes
 * @param createdAt creation timestamp
 * @param modifiedAt last modification timestamp
 * @param contentType MIME type (may be null)
 * @param customMetadata custom key-value metadata
 * @param path optional filesystem path (may be null for non-filesystem storage)
 */
public record FileMetadata(
    String filename,
    long sizeBytes,
    Instant createdAt,
    Instant modifiedAt,
    String contentType,
    Map<String, String> customMetadata,
    Path path
) {
    /**
     * Create metadata with minimal required fields.
     */
    public FileMetadata(String filename, long sizeBytes, Instant modifiedAt) {
        this(filename, sizeBytes, modifiedAt, modifiedAt, null, Collections.emptyMap(), null);
    }

    /**
     * Create metadata with path.
     */
    public FileMetadata(String filename, long sizeBytes, Instant modifiedAt, Path path) {
        this(filename, sizeBytes, modifiedAt, modifiedAt, null, Collections.emptyMap(), path);
    }

    /**
     * Create metadata without path.
     */
    public FileMetadata(String filename, long sizeBytes, Instant createdAt, Instant modifiedAt,
                        String contentType, Map<String, String> customMetadata) {
        this(filename, sizeBytes, createdAt, modifiedAt, contentType, customMetadata, null);
    }

    /**
     * Get human-readable size string.
     */
    public String getSizeFormatted() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        }
    }

    /**
     * Get custom metadata value.
     *
     * @param key metadata key
     * @return value or null if not found
     */
    public String getCustom(String key) {
        return customMetadata != null ? customMetadata.get(key) : null;
    }

    /**
     * Check if metadata has a specific custom key.
     *
     * @param key metadata key
     * @return true if key exists
     */
    public boolean hasCustom(String key) {
        return customMetadata != null && customMetadata.containsKey(key);
    }

    /**
     * Get last modified time in milliseconds (for compatibility).
     *
     * @return milliseconds since epoch
     */
    public long getLastModifiedMs() {
        return modifiedAt != null ? modifiedAt.toEpochMilli() : 0;
    }
}
