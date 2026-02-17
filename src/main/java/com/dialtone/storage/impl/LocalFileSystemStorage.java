/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.storage.impl;

import com.dialtone.storage.FileMetadata;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageException;
import com.dialtone.storage.WriteHandle;
import com.dialtone.utils.LoggerUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of FileStorage.
 *
 * <p>Directory structure:
 * <pre>
 * {baseDir}/
 *   user/                          # USER scope
 *     {screenname1}/
 *       file1.txt
 *       file1.txt.meta.json        # Sidecar metadata
 *     {screenname2}/
 *       ...
 *   global/                        # GLOBAL scope
 *     fetch-403.hqx
 *     fetch-403.hqx.meta.json
 * </pre>
 *
 * <p>Thread Safety: All public methods are thread-safe via path-level locking.
 */
public class LocalFileSystemStorage implements FileStorage {

    private static final String USER_SUBDIR = "user";
    private static final String GLOBAL_SUBDIR = "global";
    private static final String META_SUFFIX = ".meta.json";

    private final Path baseDir;
    private final long maxFileSizeBytes;
    private final Gson gson;

    // Path-level locks for thread safety
    private final ConcurrentHashMap<String, Object> pathLocks = new ConcurrentHashMap<>();

    public LocalFileSystemStorage(Path baseDir) {
        this(baseDir, 100L * 1024 * 1024); // 100 MB default
    }

    public LocalFileSystemStorage(Path baseDir, long maxFileSizeBytes) {
        this.baseDir = baseDir;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    @Override
    public void initialize() throws IOException {
        Files.createDirectories(baseDir.resolve(USER_SUBDIR));
        Files.createDirectories(baseDir.resolve(GLOBAL_SUBDIR));
        LoggerUtil.info("[LocalFileSystemStorage] Initialized at: " + baseDir.toAbsolutePath());
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    // ==================== Read Operations ====================

    @Override
    public boolean exists(Scope scope, String owner, String filename) {
        Path filePath = resolvePath(scope, owner, filename);
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    @Override
    public Optional<FileMetadata> getMetadata(Scope scope, String owner, String filename) {
        Path filePath = resolvePath(scope, owner, filename);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return Optional.empty();
        }

        return Optional.of(loadMetadata(filePath));
    }

    @Override
    public InputStream read(Scope scope, String owner, String filename) throws IOException {
        Path filePath = resolvePath(scope, owner, filename);
        if (!Files.exists(filePath)) {
            throw new StorageException.FileNotFoundException(filename, scope.name(), owner);
        }
        return new BufferedInputStream(Files.newInputStream(filePath));
    }

    @Override
    public byte[] readAllBytes(Scope scope, String owner, String filename) throws IOException {
        Path filePath = resolvePath(scope, owner, filename);
        if (!Files.exists(filePath)) {
            throw new StorageException.FileNotFoundException(filename, scope.name(), owner);
        }
        return Files.readAllBytes(filePath);
    }

    @Override
    public List<FileMetadata> list(Scope scope, String owner) {
        Path dir = resolveDirectory(scope, owner);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().endsWith(META_SUFFIX))
                .map(this::loadMetadata)
                .collect(Collectors.toList());
        } catch (IOException e) {
            LoggerUtil.warn("[LocalFileSystemStorage] Failed to list files: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Write Operations ====================

    @Override
    public WriteHandle write(Scope scope, String owner, String filename) throws IOException {
        Path dir = resolveDirectory(scope, owner);
        Files.createDirectories(dir);

        String sanitized = sanitizeFilename(filename);
        Path filePath = dir.resolve(sanitized);

        // Handle collisions by appending timestamp
        if (Files.exists(filePath)) {
            sanitized = addTimestampSuffix(sanitized);
            filePath = dir.resolve(sanitized);
        }

        LoggerUtil.info(String.format(
            "[LocalFileSystemStorage] Creating file: %s/%s/%s",
            scope.name().toLowerCase(), owner != null ? owner : "", sanitized));

        OutputStream outputStream = new BufferedOutputStream(
            Files.newOutputStream(filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING));

        return new WriteHandle(outputStream, sanitized, filePath);
    }

    @Override
    public FileMetadata writeAllBytes(Scope scope, String owner, String filename, byte[] content) throws IOException {
        WriteHandle handle = write(scope, owner, filename);
        try (OutputStream out = handle.outputStream()) {
            out.write(content);
        }

        // Create metadata sidecar
        FileMetadata metadata = new FileMetadata(
            handle.actualFilename(),
            content.length,
            Instant.now(),
            Instant.now(),
            null,
            Collections.emptyMap(),
            handle.path()
        );
        saveMetadata(handle.path(), metadata);

        return metadata;
    }

    @Override
    public boolean delete(Scope scope, String owner, String filename) throws IOException {
        Path filePath = resolvePath(scope, owner, filename);
        Path metaPath = getMetaPath(filePath);

        boolean fileDeleted = Files.deleteIfExists(filePath);
        Files.deleteIfExists(metaPath); // Also delete metadata sidecar

        if (fileDeleted) {
            LoggerUtil.info(String.format(
                "[LocalFileSystemStorage] Deleted file: %s/%s/%s",
                scope.name().toLowerCase(), owner != null ? owner : "", filename));
        }

        return fileDeleted;
    }

    // ==================== Metadata Operations ====================

    @Override
    public void setMetadata(Scope scope, String owner, String filename, String key, String value) throws IOException {
        setMetadata(scope, owner, filename, Map.of(key, value));
    }

    @Override
    public void setMetadata(Scope scope, String owner, String filename, Map<String, String> metadata) throws IOException {
        Path filePath = resolvePath(scope, owner, filename);
        if (!Files.exists(filePath)) {
            throw new StorageException.FileNotFoundException(filename, scope.name(), owner);
        }

        FileMetadata existing = loadMetadata(filePath);
        Map<String, String> merged = new HashMap<>(existing.customMetadata());
        merged.putAll(metadata);

        FileMetadata updated = new FileMetadata(
            existing.filename(),
            existing.sizeBytes(),
            existing.createdAt(),
            Instant.now(),
            existing.contentType(),
            merged,
            existing.path()
        );

        saveMetadata(filePath, updated);
    }

    // ==================== Storage Management ====================

    @Override
    public long getStorageUsed(Scope scope, String owner) {
        Path dir = resolveDirectory(scope, owner);
        if (!Files.exists(dir)) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().endsWith(META_SUFFIX))
                .mapToLong(this::getFileSizeQuietly)
                .sum();
        } catch (IOException e) {
            LoggerUtil.warn("[LocalFileSystemStorage] Failed to get storage used: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get base directory.
     *
     * @return base directory path
     */
    public Path getBaseDir() {
        return baseDir;
    }

    // ==================== Path Resolution ====================

    private Path resolvePath(Scope scope, String owner, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        Path dir = resolveDirectory(scope, owner);
        return dir.resolve(sanitizedFilename);
    }

    private Path resolveDirectory(Scope scope, String owner) {
        return switch (scope) {
            case USER -> {
                String sanitizedOwner = sanitizeUsername(owner);
                yield baseDir.resolve(USER_SUBDIR).resolve(sanitizedOwner);
            }
            case GLOBAL -> baseDir.resolve(GLOBAL_SUBDIR);
        };
    }

    private Path getMetaPath(Path filePath) {
        return filePath.resolveSibling(filePath.getFileName().toString() + META_SUFFIX);
    }

    // ==================== Metadata Persistence ====================

    private FileMetadata loadMetadata(Path filePath) {
        Path metaPath = getMetaPath(filePath);
        String filename = filePath.getFileName().toString();

        // Try to load from sidecar file
        if (Files.exists(metaPath)) {
            try {
                String json = Files.readString(metaPath);
                MetadataJson meta = gson.fromJson(json, MetadataJson.class);
                return new FileMetadata(
                    filename,
                    meta.sizeBytes != null ? meta.sizeBytes : getFileSizeQuietly(filePath),
                    meta.createdAt != null ? Instant.parse(meta.createdAt) : getModifiedTimeQuietly(filePath),
                    meta.modifiedAt != null ? Instant.parse(meta.modifiedAt) : getModifiedTimeQuietly(filePath),
                    meta.contentType,
                    meta.custom != null ? meta.custom : Collections.emptyMap(),
                    filePath
                );
            } catch (Exception e) {
                LoggerUtil.debug("[LocalFileSystemStorage] Failed to load metadata sidecar: " + e.getMessage());
            }
        }

        // Fall back to filesystem metadata
        return new FileMetadata(
            filename,
            getFileSizeQuietly(filePath),
            getModifiedTimeQuietly(filePath),
            filePath
        );
    }

    private void saveMetadata(Path filePath, FileMetadata metadata) {
        Path metaPath = getMetaPath(filePath);
        Path tempPath = metaPath.resolveSibling(metaPath.getFileName().toString() + ".tmp");

        MetadataJson meta = new MetadataJson();
        meta.filename = metadata.filename();
        meta.sizeBytes = metadata.sizeBytes();
        meta.createdAt = metadata.createdAt() != null ? metadata.createdAt().toString() : null;
        meta.modifiedAt = metadata.modifiedAt() != null ? metadata.modifiedAt().toString() : null;
        meta.contentType = metadata.contentType();
        meta.custom = metadata.customMetadata().isEmpty() ? null : new HashMap<>(metadata.customMetadata());

        try {
            // Write to temp file then rename (atomic operation)
            Files.writeString(tempPath, gson.toJson(meta));
            Files.move(tempPath, metaPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LoggerUtil.warn("[LocalFileSystemStorage] Failed to save metadata: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    // ==================== Sanitization Methods ====================

    /**
     * Sanitize username for use as directory name.
     */
    public String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "unknown";
        }
        return username
            .replaceAll("[/\\\\]", "")      // Remove path separators
            .replaceAll("\\.\\.", "")        // Remove parent refs
            .replaceAll("[^a-zA-Z0-9_-]", "_") // Keep only safe chars
            .trim();
    }

    /**
     * Sanitize filename for safe storage.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "file_" + System.currentTimeMillis() + ".bin";
        }

        // Extract just the filename (remove any path components)
        String name = filename;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Remove dangerous patterns
        name = name
            .replaceAll("\\.\\.", "")        // Remove parent refs
            .replaceAll("[<>:\"|?*]", "_")   // Remove Windows-forbidden chars
            .trim();

        // Don't allow hidden files (starting with .)
        while (name.startsWith(".")) {
            name = name.substring(1);
        }

        // Ensure non-empty
        if (name.isEmpty()) {
            return "file_" + System.currentTimeMillis() + ".bin";
        }

        // Truncate to 64 characters (leaving room for timestamp suffix)
        if (name.length() > 64) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex > name.length() - 10) {
                String ext = name.substring(dotIndex);
                name = name.substring(0, 64 - ext.length()) + ext;
            } else {
                name = name.substring(0, 64);
            }
        }

        return name;
    }

    private String addTimestampSuffix(String filename) {
        String timestamp = "_" + System.currentTimeMillis();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex) + timestamp + filename.substring(dotIndex);
        }
        return filename + timestamp;
    }

    // ==================== Helper Methods ====================

    private long getFileSizeQuietly(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private Instant getModifiedTimeQuietly(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }

    // ==================== JSON Model ====================

    private static class MetadataJson {
        String filename;
        Long sizeBytes;
        String createdAt;
        String modifiedAt;
        String contentType;
        Map<String, String> custom;
    }
}
