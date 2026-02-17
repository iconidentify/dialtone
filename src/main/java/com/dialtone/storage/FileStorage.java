/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract storage interface for file operations.
 *
 * <p>Supports both user-scoped files (uploads) and global files (downloads).
 * Implementations may be read-only (classpath resources) or read-write (filesystem).
 *
 * <p>Thread Safety: Implementations must be thread-safe for use in Netty's event loop.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link com.dialtone.storage.impl.LocalFileSystemStorage} - filesystem with metadata sidecar files</li>
 *   <li>{@link com.dialtone.storage.impl.ClasspathStorage} - read-only classpath resources</li>
 * </ul>
 */
public interface FileStorage {

    /**
     * Storage scope for file operations.
     */
    enum Scope {
        /** User-specific files (e.g., storage/user/{screenname}/) */
        USER,
        /** Global shared files (e.g., storage/global/) */
        GLOBAL
    }

    // ==================== Read Operations ====================

    /**
     * Check if a file exists.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return true if file exists
     */
    boolean exists(Scope scope, String owner, String filename);

    /**
     * Get file metadata.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return metadata if file exists, empty otherwise
     */
    Optional<FileMetadata> getMetadata(Scope scope, String owner, String filename);

    /**
     * Read file content as InputStream.
     *
     * <p>Caller is responsible for closing the stream.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return input stream for reading file content
     * @throws IOException if file not found or read error
     */
    InputStream read(Scope scope, String owner, String filename) throws IOException;

    /**
     * Read file content as byte array.
     *
     * <p>Convenience method for small files. For large files, use {@link #read(Scope, String, String)}.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return file content as byte array
     * @throws IOException if file not found or read error
     */
    byte[] readAllBytes(Scope scope, String owner, String filename) throws IOException;

    /**
     * List files for a given scope and owner.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @return list of file metadata (empty list if none found)
     */
    List<FileMetadata> list(Scope scope, String owner);

    // ==================== Write Operations ====================

    /**
     * Get an OutputStream for writing a new file.
     *
     * <p>Creates parent directories as needed. Handles filename collisions
     * according to implementation policy (e.g., timestamp suffix).
     *
     * <p>Caller is responsible for closing the stream.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return write handle with output stream and actual filename
     * @throws IOException if storage error
     * @throws UnsupportedOperationException if storage is read-only
     */
    WriteHandle write(Scope scope, String owner, String filename) throws IOException;

    /**
     * Write file content from byte array.
     *
     * <p>Convenience method for small files.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @param content file content
     * @return metadata of written file
     * @throws IOException if storage error
     * @throws UnsupportedOperationException if storage is read-only
     */
    FileMetadata writeAllBytes(Scope scope, String owner, String filename, byte[] content) throws IOException;

    /**
     * Delete a file.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @param filename the filename
     * @return true if deleted, false if not found
     * @throws IOException if deletion error
     * @throws UnsupportedOperationException if storage is read-only
     */
    boolean delete(Scope scope, String owner, String filename) throws IOException;

    // ==================== Metadata Operations ====================

    /**
     * Set custom metadata on a file.
     *
     * @param scope file scope
     * @param owner owner identifier
     * @param filename the filename
     * @param key metadata key
     * @param value metadata value
     * @throws IOException if file not found or write error
     * @throws UnsupportedOperationException if storage is read-only
     */
    void setMetadata(Scope scope, String owner, String filename, String key, String value) throws IOException;

    /**
     * Set multiple custom metadata fields on a file.
     *
     * @param scope file scope
     * @param owner owner identifier
     * @param filename the filename
     * @param metadata map of key-value pairs
     * @throws IOException if file not found or write error
     * @throws UnsupportedOperationException if storage is read-only
     */
    void setMetadata(Scope scope, String owner, String filename, Map<String, String> metadata) throws IOException;

    // ==================== Storage Management ====================

    /**
     * Initialize the storage (create directories, etc.).
     *
     * @throws IOException if initialization fails
     */
    void initialize() throws IOException;

    /**
     * Check if this storage supports write operations.
     *
     * @return true if write operations are supported
     */
    boolean isWritable();

    /**
     * Get total storage used by an owner.
     *
     * @param scope file scope
     * @param owner owner identifier (screenname for USER scope, null for GLOBAL)
     * @return total bytes used
     */
    long getStorageUsed(Scope scope, String owner);

    /**
     * Get maximum file size allowed.
     *
     * @return max file size in bytes
     */
    long getMaxFileSizeBytes();
}
