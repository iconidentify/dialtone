/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage;

/**
 * Base exception for storage operations.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Thrown when a file is not found in storage.
     */
    public static class FileNotFoundException extends StorageException {
        private final String filename;

        public FileNotFoundException(String filename) {
            super("File not found: " + filename);
            this.filename = filename;
        }

        public FileNotFoundException(String filename, String scope, String owner) {
            super(String.format("File not found: %s (scope=%s, owner=%s)", filename, scope, owner));
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }
    }

    /**
     * Thrown when storage is read-only but write was attempted.
     */
    public static class ReadOnlyStorageException extends StorageException {
        public ReadOnlyStorageException(String operation) {
            super("Storage is read-only, cannot perform: " + operation);
        }
    }

    /**
     * Thrown when file size exceeds the allowed limit.
     */
    public static class FileSizeLimitExceededException extends StorageException {
        private final long actualSize;
        private final long maxSize;

        public FileSizeLimitExceededException(long actualSize, long maxSize) {
            super(String.format("File size %d bytes exceeds limit of %d bytes", actualSize, maxSize));
            this.actualSize = actualSize;
            this.maxSize = maxSize;
        }

        public long getActualSize() {
            return actualSize;
        }

        public long getMaxSize() {
            return maxSize;
        }
    }

    /**
     * Thrown when storage initialization fails.
     */
    public static class StorageInitializationException extends StorageException {
        public StorageInitializationException(String message, Throwable cause) {
            super("Storage initialization failed: " + message, cause);
        }
    }
}
