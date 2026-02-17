/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified persistence layer for all news categories.
 * Replaces separate persistence classes (NewsPersistence, CryptoNewsPersistence, etc.).
 * Saves news content to category-specific subdirectories under base directory.
 * Example: news/crypto/, news/sports/, news/tech/
 */
public class UnifiedNewsPersistence {

    private final Path baseDirectory;
    private final ObjectMapper objectMapper;

    public UnifiedNewsPersistence(String baseDirectoryPath) {
        this.baseDirectory = Paths.get(baseDirectoryPath).toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        LoggerUtil.info("Unified news persistence initialized:");
        LoggerUtil.info("  - Base directory: " + this.baseDirectory);
        LoggerUtil.info("  - Working directory: " + System.getProperty("user.dir"));

        ensureDirectoryExists(baseDirectory);
    }

    /**
     * Save news content for a specific category.
     *
     * @param category Category name (e.g., "general", "crypto", "sports")
     * @param content News content to save
     * @param keepFileCount Maximum number of files to keep per category
     * @return Path to saved file, or null if save failed
     */
    public Path saveContent(String category, UnifiedNewsContent content, int keepFileCount) {
        try {
            Path categoryDir = getCategoryDirectory(category);
            ensureDirectoryExists(categoryDir);

            String timestamp = Instant.now().toString().replaceAll("[:]", "-");
            String filename = category + "-" + timestamp + ".json";
            Path filePath = categoryDir.resolve(filename);

            objectMapper.writeValue(filePath.toFile(), content);
            LoggerUtil.info("Saved " + category + " news to: " + filePath.getFileName());

            cleanupOldFiles(category, keepFileCount);

            return filePath;

        } catch (IOException e) {
            LoggerUtil.error("Failed to save " + category + " news to JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load the most recent news content for a specific category.
     *
     * @param category Category name
     * @return Latest news content, or null if no files found
     */
    public UnifiedNewsContent loadLatestContent(String category) {
        try {
            List<File> files = getFilesSortedByTimestamp(category);

            if (files.isEmpty()) {
                LoggerUtil.debug(() -> "No existing " + category + " news files found");
                return null;
            }

            File latestFile = files.get(0);
            UnifiedNewsContent content = objectMapper.readValue(latestFile, UnifiedNewsContent.class);
            LoggerUtil.info("Loaded " + category + " news from: " + latestFile.getName());

            return content;

        } catch (IOException e) {
            LoggerUtil.error("Failed to load " + category + " news from JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load fetch metadata for a specific category.
     *
     * @param category Category name
     * @param dailyQuotaLimit Daily quota limit for this category
     * @return FetchMetadata or null if file doesn't exist
     */
    public FetchMetadata loadFetchMetadata(String category, int dailyQuotaLimit) {
        try {
            Path categoryDir = getCategoryDirectory(category);
            Path metadataPath = categoryDir.resolve("fetch-metadata.json");

            if (!Files.exists(metadataPath)) {
                return null;
            }

            FetchMetadata metadata = objectMapper.readValue(metadataPath.toFile(), FetchMetadata.class);
            LoggerUtil.info("Loaded " + category + " fetch metadata: " + metadata);
            return metadata;

        } catch (IOException e) {
            LoggerUtil.error("Failed to load " + category + " fetch metadata: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save fetch metadata for a specific category.
     *
     * @param category Category name
     * @param metadata Metadata to save
     */
    public void saveFetchMetadata(String category, FetchMetadata metadata) {
        try {
            Path categoryDir = getCategoryDirectory(category);
            ensureDirectoryExists(categoryDir);

            Path metadataPath = categoryDir.resolve("fetch-metadata.json");
            objectMapper.writeValue(metadataPath.toFile(), metadata);
            LoggerUtil.debug(() -> "Saved " + category + " fetch metadata: " + metadata);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save " + category + " fetch metadata: " + e.getMessage());
        }
    }

    /**
     * Get the directory for a specific category.
     *
     * @param category Category name
     * @return Path to category directory
     */
    private Path getCategoryDirectory(String category) {
        return baseDirectory.resolve(category);
    }

    /**
     * Ensure a directory exists, create if it doesn't.
     *
     * @param directory Directory path
     */
    private void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                LoggerUtil.info("Created news directory: " + directory);
            }
        } catch (IOException e) {
            LoggerUtil.error("Failed to create/access news directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all news files for a category sorted by timestamp (newest first).
     *
     * @param category Category name
     * @return List of files sorted by modification time
     */
    private List<File> getFilesSortedByTimestamp(String category) throws IOException {
        Path categoryDir = getCategoryDirectory(category);
        File dir = categoryDir.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            return List.of();
        }

        File[] files = dir.listFiles((d, name) ->
                name.startsWith(category + "-") && name.endsWith(".json") && !name.equals("fetch-metadata.json"));

        if (files == null || files.length == 0) {
            return List.of();
        }

        return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Clean up old news files for a category, keeping only the most recent N files.
     *
     * @param category Category name
     * @param keepFileCount Number of files to keep
     */
    private void cleanupOldFiles(String category, int keepFileCount) {
        try {
            List<File> files = getFilesSortedByTimestamp(category);

            if (files.size() <= keepFileCount) {
                return;
            }

            List<File> filesToDelete = files.subList(keepFileCount, files.size());
            int deletedCount = 0;

            for (File file : filesToDelete) {
                if (file.delete()) {
                    deletedCount++;
                    LoggerUtil.debug(() -> "Deleted old " + category + " news file: " + file.getName());
                }
            }

            if (deletedCount > 0) {
                LoggerUtil.info("Cleaned up " + deletedCount + " old " + category + " news files");
            }

        } catch (IOException e) {
            LoggerUtil.error("Failed to cleanup old " + category + " news files: " + e.getMessage());
        }
    }

    /**
     * Get the count of existing news files for a category.
     *
     * @param category Category name
     * @return Number of news JSON files in the category directory
     */
    public int getFileCount(String category) {
        try {
            return getFilesSortedByTimestamp(category).size();
        } catch (IOException e) {
            return 0;
        }
    }
}
