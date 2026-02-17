/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.filebrowser;

import com.dialtone.storage.FileStorage;
import com.dialtone.storage.impl.LocalFileSystemStorage;
import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service layer for file browser functionality.
 *
 * <p>Provides path handling, pagination, and file/directory listing for the
 * file browser UI. Operates within the GLOBAL storage scope only.
 *
 * <p>State is encoded as Base64 tokens for stateless navigation:
 * {@code FB:<base64_path>:<page>}
 *
 * <p>Security: All paths are validated to prevent directory traversal attacks.
 * Only paths within the storage root are accessible.
 */
public class FileBrowserService {

    private static final int DEFAULT_ITEMS_PER_PAGE = 10;
    private static final String STATE_PREFIX = "FB:";

    private final Path storageRoot;
    private final int itemsPerPage;

    /**
     * Result of browsing a directory.
     *
     * @param currentPath   current directory path relative to storage root
     * @param parentPath    parent directory path (null if at root)
     * @param entries       list of file/directory entries for current page
     * @param currentPage   current page number (1-indexed)
     * @param totalPages    total number of pages
     * @param totalItems    total number of items in directory
     */
    public record BrowseResult(
        String currentPath,
        String parentPath,
        List<FileEntry> entries,
        int currentPage,
        int totalPages,
        int totalItems
    ) {}

    /**
     * A file or directory entry.
     *
     * @param name          filename or directory name
     * @param isDirectory   true if this is a directory
     * @param sizeBytes     file size in bytes (0 for directories)
     * @param formattedSize human-readable size string
     * @param modifiedAt    last modification time
     */
    public record FileEntry(
        String name,
        boolean isDirectory,
        long sizeBytes,
        String formattedSize,
        Instant modifiedAt
    ) {}

    /**
     * Decoded navigation state.
     *
     * @param path relative path within storage
     * @param page page number (1-indexed)
     */
    public record BrowseState(String path, int page) {}

    /**
     * Creates a FileBrowserService using the storage root from FileStorage.
     *
     * @param fileStorage the file storage (must be LocalFileSystemStorage)
     */
    public FileBrowserService(FileStorage fileStorage) {
        this(fileStorage, DEFAULT_ITEMS_PER_PAGE);
    }

    /**
     * Creates a FileBrowserService with custom pagination.
     *
     * @param fileStorage  the file storage (must be LocalFileSystemStorage)
     * @param itemsPerPage number of items per page
     */
    public FileBrowserService(FileStorage fileStorage, int itemsPerPage) {
        if (fileStorage instanceof LocalFileSystemStorage localFs) {
            this.storageRoot = localFs.getBaseDir().resolve("global");
        } else {
            throw new IllegalArgumentException(
                "FileBrowserService requires LocalFileSystemStorage, got: " +
                fileStorage.getClass().getSimpleName());
        }
        this.itemsPerPage = itemsPerPage > 0 ? itemsPerPage : DEFAULT_ITEMS_PER_PAGE;
    }

    /**
     * Creates a FileBrowserService with explicit storage root.
     *
     * @param storageRoot  root directory for browsing
     * @param itemsPerPage number of items per page
     */
    public FileBrowserService(Path storageRoot, int itemsPerPage) {
        this.storageRoot = storageRoot;
        this.itemsPerPage = itemsPerPage > 0 ? itemsPerPage : DEFAULT_ITEMS_PER_PAGE;
    }

    /**
     * Browse a directory and return paginated results.
     *
     * @param relativePath path relative to storage root (empty or "/" for root)
     * @param page         page number (1-indexed, defaults to 1 if invalid)
     * @return browse result with entries and pagination info
     */
    public BrowseResult browse(String relativePath, int page) {
        // Normalize path
        String normalizedPath = normalizePath(relativePath);

        // Resolve and validate path
        Path targetDir = resolveAndValidatePath(normalizedPath);
        if (targetDir == null || !Files.isDirectory(targetDir)) {
            LoggerUtil.warn("[FileBrowserService] Invalid or non-existent path: " + normalizedPath);
            return new BrowseResult(normalizedPath, null, Collections.emptyList(), 1, 0, 0);
        }

        // List all entries
        List<FileEntry> allEntries = listDirectory(targetDir);
        int totalItems = allEntries.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
        totalPages = Math.max(1, totalPages);

        // Clamp page number
        int currentPage = Math.max(1, Math.min(page, totalPages));

        // Get page slice
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        List<FileEntry> pageEntries = allEntries.subList(startIndex, endIndex);

        // Calculate parent path
        String parentPath = getParentPath(normalizedPath);

        return new BrowseResult(
            normalizedPath,
            parentPath,
            pageEntries,
            currentPage,
            totalPages,
            totalItems
        );
    }

    /**
     * Encode navigation state as a Base64 payload.
     *
     * @param path relative path
     * @param page page number
     * @return encoded state string
     */
    public String encodeState(String path, int page) {
        String normalized = normalizePath(path);
        String data = normalized + ":" + page;
        String base64 = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        return STATE_PREFIX + base64;
    }

    /**
     * Decode navigation state from payload.
     *
     * @param payload encoded state (with or without FB: prefix)
     * @return decoded state, or root/page 1 if invalid
     */
    public BrowseState decodeState(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new BrowseState("/", 1);
        }

        String toDecode = payload;
        if (toDecode.startsWith(STATE_PREFIX)) {
            toDecode = toDecode.substring(STATE_PREFIX.length());
        }

        try {
            String decoded = new String(
                Base64.getDecoder().decode(toDecode),
                StandardCharsets.UTF_8
            );

            int lastColon = decoded.lastIndexOf(':');
            if (lastColon > 0 && lastColon < decoded.length() - 1) {
                String path = decoded.substring(0, lastColon);
                int page = Integer.parseInt(decoded.substring(lastColon + 1));
                return new BrowseState(normalizePath(path), Math.max(1, page));
            }
        } catch (IllegalArgumentException e) {
            LoggerUtil.warn("[FileBrowserService] Failed to decode state: " + e.getMessage());
        }

        return new BrowseState("/", 1);
    }

    /**
     * Check if a path is valid and within the storage root.
     *
     * @param relativePath path to validate
     * @return true if valid
     */
    public boolean isValidPath(String relativePath) {
        return resolveAndValidatePath(normalizePath(relativePath)) != null;
    }

    /**
     * Get the parent path of a given path.
     *
     * @param relativePath current path
     * @return parent path, or null if at root
     */
    public String getParentPath(String relativePath) {
        String normalized = normalizePath(relativePath);
        if (normalized.equals("/") || normalized.isEmpty()) {
            return null;
        }

        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return normalized.substring(0, lastSlash);
    }

    /**
     * Get the storage root path.
     *
     * @return storage root
     */
    public Path getStorageRoot() {
        return storageRoot;
    }

    /**
     * Get items per page.
     *
     * @return items per page
     */
    public int getItemsPerPage() {
        return itemsPerPage;
    }

    // ==================== Private Methods ====================

    /**
     * Normalize a path to consistent format.
     * - Always starts with /
     * - No trailing slash (except for root)
     * - No double slashes
     * - No . or .. components
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }

        // Remove any dangerous patterns
        String safe = path
            .replace("\\", "/")           // Normalize separators
            .replaceAll("/+", "/")        // Remove double slashes
            .replaceAll("\\.\\.", "")     // Remove parent refs
            .replaceAll("/\\./", "/")     // Remove current dir refs
            .trim();

        // Ensure starts with /
        if (!safe.startsWith("/")) {
            safe = "/" + safe;
        }

        // Remove trailing slash (except for root)
        while (safe.length() > 1 && safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }

        return safe.isEmpty() ? "/" : safe;
    }

    /**
     * Resolve a relative path and validate it's within storage root.
     *
     * @param relativePath normalized relative path
     * @return resolved path, or null if invalid/outside root
     */
    private Path resolveAndValidatePath(String relativePath) {
        try {
            // Strip leading slash for resolution
            String pathPart = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;

            Path resolved;
            if (pathPart.isEmpty()) {
                resolved = storageRoot;
            } else {
                resolved = storageRoot.resolve(pathPart);
            }

            // Normalize and verify within root
            Path normalized = resolved.normalize().toAbsolutePath();
            Path rootNormalized = storageRoot.normalize().toAbsolutePath();

            if (!normalized.startsWith(rootNormalized)) {
                LoggerUtil.warn("[FileBrowserService] Path traversal blocked: " + relativePath);
                return null;
            }

            if (!Files.exists(normalized)) {
                return null;
            }

            return normalized;
        } catch (Exception e) {
            LoggerUtil.warn("[FileBrowserService] Path resolution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * List directory contents, sorted with directories first.
     */
    private List<FileEntry> listDirectory(Path directory) {
        List<FileEntry> entries = new ArrayList<>();

        try (Stream<Path> paths = Files.list(directory)) {
            paths.forEach(path -> {
                try {
                    // Skip metadata sidecar files
                    if (path.getFileName().toString().endsWith(".meta.json")) {
                        return;
                    }

                    boolean isDir = Files.isDirectory(path);
                    long size = isDir ? 0 : Files.size(path);
                    Instant modified = Files.getLastModifiedTime(path).toInstant();

                    entries.add(new FileEntry(
                        path.getFileName().toString(),
                        isDir,
                        size,
                        formatSize(size),
                        modified
                    ));
                } catch (IOException e) {
                    LoggerUtil.debug("[FileBrowserService] Skipping unreadable: " + path);
                }
            });
        } catch (IOException e) {
            LoggerUtil.warn("[FileBrowserService] Failed to list directory: " + e.getMessage());
        }

        // Sort: directories first, then by name (case-insensitive)
        entries.sort(Comparator
            .comparing((FileEntry e) -> !e.isDirectory())
            .thenComparing(e -> e.name().toLowerCase()));

        return entries;
    }

    /**
     * Format file size as human-readable string.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
