/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage;

import com.dialtone.storage.impl.ClasspathStorage;
import com.dialtone.storage.impl.LocalFileSystemStorage;
import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Factory for creating FileStorage instances based on configuration.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>storage.type - "local" (default) or "classpath"</li>
 *   <li>storage.local.base.dir - base directory for local storage (default: "storage")</li>
 *   <li>storage.local.max.file.size.mb - max file size in MB (default: 100)</li>
 * </ul>
 */
public class StorageFactory {

    private static final String DEFAULT_BASE_DIR = "storage";
    private static final long DEFAULT_MAX_FILE_SIZE_MB = 100;

    /**
     * Create FileStorage from configuration.
     *
     * @param config application properties
     * @return configured FileStorage instance
     * @throws IOException if initialization fails
     */
    public static FileStorage create(Properties config) throws IOException {
        String type = config.getProperty("storage.type", "local");

        FileStorage storage = switch (type.toLowerCase()) {
            case "local" -> createLocalStorage(config);
            case "classpath" -> createClasspathStorage(config);
            default -> {
                LoggerUtil.warn("[StorageFactory] Unknown storage type '" + type + "', falling back to local");
                yield createLocalStorage(config);
            }
        };

        storage.initialize();
        return storage;
    }

    /**
     * Create local filesystem storage.
     *
     * @param config application properties
     * @return LocalFileSystemStorage instance
     */
    public static LocalFileSystemStorage createLocalStorage(Properties config) {
        String baseDir = config.getProperty("storage.local.base.dir", DEFAULT_BASE_DIR);
        long maxSizeMb = parseLong(config, "storage.local.max.file.size.mb", DEFAULT_MAX_FILE_SIZE_MB);

        LoggerUtil.info("[StorageFactory] Creating LocalFileSystemStorage at: " + baseDir);

        return new LocalFileSystemStorage(
            Paths.get(baseDir),
            maxSizeMb * 1024 * 1024
        );
    }

    /**
     * Create classpath storage (read-only).
     *
     * @param config application properties
     * @return ClasspathStorage instance
     */
    public static ClasspathStorage createClasspathStorage(Properties config) {
        String prefix = config.getProperty("storage.classpath.prefix", "downloads/");

        LoggerUtil.info("[StorageFactory] Creating ClasspathStorage with prefix: " + prefix);

        return new ClasspathStorage(prefix);
    }

    /**
     * Create a composite storage that tries local first, then falls back to classpath.
     *
     * <p>This is useful for downloads where files may exist on the filesystem
     * (for Docker volume mounts) or in the JAR (for local development).
     *
     * @param config application properties
     * @return CompositeStorage instance
     * @throws IOException if initialization fails
     */
    public static FileStorage createWithFallback(Properties config) throws IOException {
        LocalFileSystemStorage primary = createLocalStorage(config);
        ClasspathStorage fallback = createClasspathStorage(config);

        primary.initialize();
        fallback.initialize();

        return new CompositeStorage(primary, fallback);
    }

    private static long parseLong(Properties config, String key, long defaultValue) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LoggerUtil.warn("[StorageFactory] Invalid value for " + key + ": " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Composite storage that tries primary storage first, then falls back to secondary.
     *
     * <p>Write operations go to primary storage only. Read operations try primary
     * first, then secondary if not found.
     */
    private static class CompositeStorage implements FileStorage {

        private final FileStorage primary;
        private final FileStorage fallback;

        CompositeStorage(FileStorage primary, FileStorage fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public void initialize() throws IOException {
            // Already initialized in createWithFallback
        }

        @Override
        public boolean isWritable() {
            return primary.isWritable();
        }

        @Override
        public long getMaxFileSizeBytes() {
            return primary.getMaxFileSizeBytes();
        }

        @Override
        public boolean exists(Scope scope, String owner, String filename) {
            return primary.exists(scope, owner, filename) || fallback.exists(scope, owner, filename);
        }

        @Override
        public java.util.Optional<FileMetadata> getMetadata(Scope scope, String owner, String filename) {
            java.util.Optional<FileMetadata> result = primary.getMetadata(scope, owner, filename);
            if (result.isPresent()) {
                return result;
            }
            return fallback.getMetadata(scope, owner, filename);
        }

        @Override
        public java.io.InputStream read(Scope scope, String owner, String filename) throws IOException {
            if (primary.exists(scope, owner, filename)) {
                return primary.read(scope, owner, filename);
            }
            return fallback.read(scope, owner, filename);
        }

        @Override
        public byte[] readAllBytes(Scope scope, String owner, String filename) throws IOException {
            if (primary.exists(scope, owner, filename)) {
                return primary.readAllBytes(scope, owner, filename);
            }
            return fallback.readAllBytes(scope, owner, filename);
        }

        @Override
        public java.util.List<FileMetadata> list(Scope scope, String owner) {
            // Only list from primary (can't merge easily and fallback can't list anyway)
            return primary.list(scope, owner);
        }

        @Override
        public WriteHandle write(Scope scope, String owner, String filename) throws IOException {
            return primary.write(scope, owner, filename);
        }

        @Override
        public FileMetadata writeAllBytes(Scope scope, String owner, String filename, byte[] content) throws IOException {
            return primary.writeAllBytes(scope, owner, filename, content);
        }

        @Override
        public boolean delete(Scope scope, String owner, String filename) throws IOException {
            return primary.delete(scope, owner, filename);
        }

        @Override
        public void setMetadata(Scope scope, String owner, String filename, String key, String value) throws IOException {
            primary.setMetadata(scope, owner, filename, key, value);
        }

        @Override
        public void setMetadata(Scope scope, String owner, String filename, java.util.Map<String, String> metadata) throws IOException {
            primary.setMetadata(scope, owner, filename, metadata);
        }

        @Override
        public long getStorageUsed(Scope scope, String owner) {
            return primary.getStorageUsed(scope, owner);
        }
    }
}
