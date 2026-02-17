/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage.impl;

import com.dialtone.storage.FileMetadata;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageException;
import com.dialtone.storage.WriteHandle;
import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only storage implementation backed by classpath resources.
 *
 * <p>Used as a fallback for bundled files (e.g., downloads embedded in JAR).
 * Only supports GLOBAL scope for reading; all writes throw UnsupportedOperationException.
 *
 * <p>Resources are expected at: {resourcePrefix}/{filename}
 * Default prefix is "downloads/" for backwards compatibility.
 */
public class ClasspathStorage implements FileStorage {

    private final String resourcePrefix;

    /**
     * Create classpath storage with default "downloads/" prefix.
     */
    public ClasspathStorage() {
        this("downloads/");
    }

    /**
     * Create classpath storage with custom resource prefix.
     *
     * @param resourcePrefix prefix for classpath resource lookups (e.g., "downloads/")
     */
    public ClasspathStorage(String resourcePrefix) {
        this.resourcePrefix = resourcePrefix != null ? resourcePrefix : "downloads/";
    }

    @Override
    public void initialize() throws IOException {
        LoggerUtil.info("[ClasspathStorage] Initialized with prefix: " + resourcePrefix);
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public long getMaxFileSizeBytes() {
        return Long.MAX_VALUE; // No limit for reading
    }

    // ==================== Read Operations ====================

    @Override
    public boolean exists(Scope scope, String owner, String filename) {
        if (scope != Scope.GLOBAL) {
            return false; // Only GLOBAL scope supported for classpath
        }

        String resourcePath = resourcePrefix + sanitizeFilename(filename);
        return getClass().getClassLoader().getResource(resourcePath) != null;
    }

    @Override
    public Optional<FileMetadata> getMetadata(Scope scope, String owner, String filename) {
        if (scope != Scope.GLOBAL) {
            return Optional.empty();
        }

        String resourcePath = resourcePrefix + sanitizeFilename(filename);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return Optional.empty();
            }

            // Read to get size (necessary for classpath resources)
            byte[] content = is.readAllBytes();
            return Optional.of(new FileMetadata(
                filename,
                content.length,
                Instant.EPOCH // Unknown creation time for classpath resources
            ));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public InputStream read(Scope scope, String owner, String filename) throws IOException {
        if (scope != Scope.GLOBAL) {
            throw new StorageException.FileNotFoundException(filename, scope.name(), owner);
        }

        String resourcePath = resourcePrefix + sanitizeFilename(filename);
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new StorageException.FileNotFoundException(filename);
        }

        LoggerUtil.debug("[ClasspathStorage] Reading resource: " + resourcePath);
        return is;
    }

    @Override
    public byte[] readAllBytes(Scope scope, String owner, String filename) throws IOException {
        try (InputStream is = read(scope, owner, filename)) {
            return is.readAllBytes();
        }
    }

    @Override
    public List<FileMetadata> list(Scope scope, String owner) {
        // Classpath resources cannot be listed dynamically
        // Would need to scan JAR contents or maintain a manifest
        return Collections.emptyList();
    }

    // ==================== Write Operations (Not Supported) ====================

    @Override
    public WriteHandle write(Scope scope, String owner, String filename) throws IOException {
        throw new StorageException.ReadOnlyStorageException("write");
    }

    @Override
    public FileMetadata writeAllBytes(Scope scope, String owner, String filename, byte[] content) throws IOException {
        throw new StorageException.ReadOnlyStorageException("writeAllBytes");
    }

    @Override
    public boolean delete(Scope scope, String owner, String filename) throws IOException {
        throw new StorageException.ReadOnlyStorageException("delete");
    }

    // ==================== Metadata Operations (Not Supported) ====================

    @Override
    public void setMetadata(Scope scope, String owner, String filename, String key, String value) throws IOException {
        throw new StorageException.ReadOnlyStorageException("setMetadata");
    }

    @Override
    public void setMetadata(Scope scope, String owner, String filename, Map<String, String> metadata) throws IOException {
        throw new StorageException.ReadOnlyStorageException("setMetadata");
    }

    // ==================== Storage Management ====================

    @Override
    public long getStorageUsed(Scope scope, String owner) {
        return 0; // Cannot determine for classpath resources
    }

    // ==================== Helper Methods ====================

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // Remove path separators and parent references
        return filename
            .replaceAll("[/\\\\]", "")
            .replaceAll("\\.\\.", "");
    }
}
