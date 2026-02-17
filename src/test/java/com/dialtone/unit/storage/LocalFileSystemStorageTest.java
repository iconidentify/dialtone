/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.storage;

import com.dialtone.storage.FileMetadata;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageException;
import com.dialtone.storage.WriteHandle;
import com.dialtone.storage.impl.LocalFileSystemStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalFileSystemStorage implementation.
 */
@DisplayName("LocalFileSystemStorage")
class LocalFileSystemStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileSystemStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalFileSystemStorage(tempDir, 100 * 1024 * 1024); // 100MB limit
        storage.initialize();
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should create user and global directories on initialize")
        void shouldCreateDirectoriesOnInitialize() {
            assertTrue(Files.isDirectory(tempDir.resolve("user")));
            assertTrue(Files.isDirectory(tempDir.resolve("global")));
        }

        @Test
        @DisplayName("should be idempotent on multiple initialize calls")
        void shouldBeIdempotentOnMultipleInitialize() throws IOException {
            storage.initialize();
            storage.initialize();
            assertTrue(Files.isDirectory(tempDir.resolve("user")));
            assertTrue(Files.isDirectory(tempDir.resolve("global")));
        }

        @Test
        @DisplayName("should report writable")
        void shouldReportWritable() {
            assertTrue(storage.isWritable());
        }

        @Test
        @DisplayName("should return configured max file size")
        void shouldReturnConfiguredMaxFileSize() {
            assertEquals(100 * 1024 * 1024, storage.getMaxFileSizeBytes());
        }

        @Test
        @DisplayName("should use default max file size of 100MB")
        void shouldUseDefaultMaxFileSize() throws IOException {
            LocalFileSystemStorage defaultStorage = new LocalFileSystemStorage(tempDir);
            assertEquals(100 * 1024 * 1024, defaultStorage.getMaxFileSizeBytes());
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("should return true for existing user file")
        void shouldReturnTrueForExistingUserFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "content");

            assertTrue(storage.exists(FileStorage.Scope.USER, "testuser", "test.txt"));
        }

        @Test
        @DisplayName("should return true for existing global file")
        void shouldReturnTrueForExistingGlobalFile() throws IOException {
            Path globalDir = tempDir.resolve("global");
            Files.writeString(globalDir.resolve("test.txt"), "content");

            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, "test.txt"));
        }

        @Test
        @DisplayName("should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() {
            assertFalse(storage.exists(FileStorage.Scope.USER, "testuser", "nofile.txt"));
        }

        @Test
        @DisplayName("should return false for directory")
        void shouldReturnFalseForDirectory() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir.resolve("subdir"));

            assertFalse(storage.exists(FileStorage.Scope.USER, "testuser", "subdir"));
        }

        @Test
        @DisplayName("should handle null owner for global scope")
        void shouldHandleNullOwnerForGlobalScope() throws IOException {
            Path globalDir = tempDir.resolve("global");
            Files.writeString(globalDir.resolve("test.txt"), "content");

            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, "test.txt"));
        }
    }

    @Nested
    @DisplayName("getMetadata()")
    class GetMetadataTests {

        @Test
        @DisplayName("should return metadata for existing file")
        void shouldReturnMetadataForExistingFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "hello world");

            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", "test.txt");

            assertTrue(metadata.isPresent());
            assertEquals("test.txt", metadata.get().filename());
            assertEquals(11, metadata.get().sizeBytes());
        }

        @Test
        @DisplayName("should return empty for non-existent file")
        void shouldReturnEmptyForNonExistentFile() {
            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", "nofile.txt");

            assertTrue(metadata.isEmpty());
        }

        @Test
        @DisplayName("should load metadata from sidecar file")
        void shouldLoadMetadataFromSidecarFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "hello");

            // Create sidecar metadata file
            String metaJson = """
                {
                    "filename": "test.txt",
                    "sizeBytes": 5,
                    "contentType": "text/plain",
                    "custom": {"source": "test"}
                }
                """;
            Files.writeString(userDir.resolve("test.txt.meta.json"), metaJson);

            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", "test.txt");

            assertTrue(metadata.isPresent());
            assertEquals("text/plain", metadata.get().contentType());
            assertEquals("test", metadata.get().getCustom("source"));
        }
    }

    @Nested
    @DisplayName("read()")
    class ReadTests {

        @Test
        @DisplayName("should read existing file")
        void shouldReadExistingFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "hello world");

            try (InputStream is = storage.read(FileStorage.Scope.USER, "testuser", "test.txt")) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals("hello world", content);
            }
        }

        @Test
        @DisplayName("should throw FileNotFoundException for missing file")
        void shouldThrowFileNotFoundForMissingFile() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.read(FileStorage.Scope.USER, "testuser", "nofile.txt"));
        }

        @Test
        @DisplayName("should read global scoped file")
        void shouldReadGlobalScopedFile() throws IOException {
            Path globalDir = tempDir.resolve("global");
            Files.writeString(globalDir.resolve("test.txt"), "global content");

            try (InputStream is = storage.read(FileStorage.Scope.GLOBAL, null, "test.txt")) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals("global content", content);
            }
        }
    }

    @Nested
    @DisplayName("readAllBytes()")
    class ReadAllBytesTests {

        @Test
        @DisplayName("should read all bytes from existing file")
        void shouldReadAllBytesFromExistingFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "hello world");

            byte[] content = storage.readAllBytes(FileStorage.Scope.USER, "testuser", "test.txt");
            assertEquals("hello world", new String(content, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should throw FileNotFoundException for missing file")
        void shouldThrowFileNotFoundForMissingFile() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.readAllBytes(FileStorage.Scope.USER, "testuser", "nofile.txt"));
        }

        @Test
        @DisplayName("should handle empty file")
        void shouldHandleEmptyFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("empty.txt"), "");

            byte[] content = storage.readAllBytes(FileStorage.Scope.USER, "testuser", "empty.txt");
            assertEquals(0, content.length);
        }
    }

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("should list files in user directory")
        void shouldListFilesInUserDirectory() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("file1.txt"), "content1");
            Files.writeString(userDir.resolve("file2.txt"), "content2");

            List<FileMetadata> files = storage.list(FileStorage.Scope.USER, "testuser");

            assertEquals(2, files.size());
        }

        @Test
        @DisplayName("should return empty list for non-existent directory")
        void shouldReturnEmptyListForNonExistentDirectory() {
            List<FileMetadata> files = storage.list(FileStorage.Scope.USER, "nonexistent");
            assertTrue(files.isEmpty());
        }

        @Test
        @DisplayName("should exclude metadata sidecar files")
        void shouldExcludeMetadataSidecarFiles() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("file.txt"), "content");
            Files.writeString(userDir.resolve("file.txt.meta.json"), "{}");

            List<FileMetadata> files = storage.list(FileStorage.Scope.USER, "testuser");

            assertEquals(1, files.size());
            assertEquals("file.txt", files.get(0).filename());
        }

        @Test
        @DisplayName("should exclude subdirectories")
        void shouldExcludeSubdirectories() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir.resolve("subdir"));
            Files.writeString(userDir.resolve("file.txt"), "content");

            List<FileMetadata> files = storage.list(FileStorage.Scope.USER, "testuser");

            assertEquals(1, files.size());
        }
    }

    @Nested
    @DisplayName("write()")
    class WriteTests {

        @Test
        @DisplayName("should create file and return write handle")
        void shouldCreateFileAndReturnWriteHandle() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", "newfile.txt");

            assertNotNull(handle.outputStream());
            assertNotNull(handle.actualFilename());

            try (OutputStream os = handle.outputStream()) {
                os.write("test content".getBytes(StandardCharsets.UTF_8));
            }

            assertTrue(storage.exists(FileStorage.Scope.USER, "testuser", handle.actualFilename()));
        }

        @Test
        @DisplayName("should create parent directories automatically")
        void shouldCreateParentDirectoriesAutomatically() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "newuser", "file.txt");

            try (OutputStream os = handle.outputStream()) {
                os.write("content".getBytes(StandardCharsets.UTF_8));
            }

            assertTrue(Files.isDirectory(tempDir.resolve("user").resolve("newuser")));
        }

        @Test
        @DisplayName("should sanitize filename")
        void shouldSanitizeFilename() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", "../../../etc/passwd");

            // Should sanitize and not allow path traversal
            assertFalse(handle.actualFilename().contains(".."));
            assertFalse(handle.actualFilename().contains("/"));
        }
    }

    @Nested
    @DisplayName("writeAllBytes()")
    class WriteAllBytesTests {

        @Test
        @DisplayName("should write content and return metadata")
        void shouldWriteContentAndReturnMetadata() throws IOException {
            byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

            FileMetadata metadata = storage.writeAllBytes(
                    FileStorage.Scope.USER, "testuser", "test.txt", content);

            assertNotNull(metadata);
            assertEquals(11, metadata.sizeBytes());
            assertTrue(storage.exists(FileStorage.Scope.USER, "testuser", metadata.filename()));
        }

        @Test
        @DisplayName("should create metadata sidecar file")
        void shouldCreateMetadataSidecarFile() throws IOException {
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            FileMetadata metadata = storage.writeAllBytes(
                    FileStorage.Scope.USER, "testuser", "test.txt", content);

            Path metaPath = tempDir.resolve("user").resolve("testuser")
                    .resolve(metadata.filename() + ".meta.json");
            assertTrue(Files.exists(metaPath));
        }

        @Test
        @DisplayName("should handle empty content")
        void shouldHandleEmptyContent() throws IOException {
            byte[] content = new byte[0];

            FileMetadata metadata = storage.writeAllBytes(
                    FileStorage.Scope.USER, "testuser", "empty.txt", content);

            assertEquals(0, metadata.sizeBytes());
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should delete existing file")
        void shouldDeleteExistingFile() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "content");

            boolean deleted = storage.delete(FileStorage.Scope.USER, "testuser", "test.txt");

            assertTrue(deleted);
            assertFalse(storage.exists(FileStorage.Scope.USER, "testuser", "test.txt"));
        }

        @Test
        @DisplayName("should delete metadata sidecar too")
        void shouldDeleteMetadataSidecarToo() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "content");
            Files.writeString(userDir.resolve("test.txt.meta.json"), "{}");

            storage.delete(FileStorage.Scope.USER, "testuser", "test.txt");

            assertFalse(Files.exists(userDir.resolve("test.txt.meta.json")));
        }

        @Test
        @DisplayName("should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() throws IOException {
            boolean deleted = storage.delete(FileStorage.Scope.USER, "testuser", "nofile.txt");
            assertFalse(deleted);
        }
    }

    @Nested
    @DisplayName("setMetadata()")
    class SetMetadataTests {

        @Test
        @DisplayName("should set single metadata key")
        void shouldSetSingleMetadataKey() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "content");

            storage.setMetadata(FileStorage.Scope.USER, "testuser", "test.txt",
                    "customKey", "customValue");

            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", "test.txt");
            assertTrue(metadata.isPresent());
            assertEquals("customValue", metadata.get().getCustom("customKey"));
        }

        @Test
        @DisplayName("should throw FileNotFoundException for missing file")
        void shouldThrowFileNotFoundForMissingFile() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.setMetadata(FileStorage.Scope.USER, "testuser", "nofile.txt",
                            "key", "value"));
        }

        @Test
        @DisplayName("should set multiple metadata keys")
        void shouldSetMultipleMetadataKeys() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("test.txt"), "content");

            storage.setMetadata(FileStorage.Scope.USER, "testuser", "test.txt",
                    Map.of("key1", "value1", "key2", "value2"));

            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", "test.txt");
            assertTrue(metadata.isPresent());
            assertEquals("value1", metadata.get().getCustom("key1"));
            assertEquals("value2", metadata.get().getCustom("key2"));
        }
    }

    @Nested
    @DisplayName("getStorageUsed()")
    class GetStorageUsedTests {

        @Test
        @DisplayName("should calculate total storage for user")
        void shouldCalculateTotalStorageForUser() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("file1.txt"), "12345"); // 5 bytes
            Files.writeString(userDir.resolve("file2.txt"), "1234567890"); // 10 bytes

            long used = storage.getStorageUsed(FileStorage.Scope.USER, "testuser");

            assertEquals(15, used);
        }

        @Test
        @DisplayName("should exclude metadata files from calculation")
        void shouldExcludeMetadataFilesFromCalculation() throws IOException {
            Path userDir = tempDir.resolve("user").resolve("testuser");
            Files.createDirectories(userDir);
            Files.writeString(userDir.resolve("file.txt"), "12345"); // 5 bytes
            Files.writeString(userDir.resolve("file.txt.meta.json"), "{}"); // Should not count

            long used = storage.getStorageUsed(FileStorage.Scope.USER, "testuser");

            assertEquals(5, used);
        }

        @Test
        @DisplayName("should return zero for non-existent directory")
        void shouldReturnZeroForNonExistentDirectory() {
            long used = storage.getStorageUsed(FileStorage.Scope.USER, "nonexistent");
            assertEquals(0, used);
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("should handle concurrent writes to different files")
        void shouldHandleConcurrentWrites() throws Exception {
            int numThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                final int fileNum = i;
                executor.submit(() -> {
                    try {
                        storage.writeAllBytes(
                                FileStorage.Scope.USER,
                                "testuser",
                                "file" + fileNum + ".txt",
                                ("content" + fileNum).getBytes(StandardCharsets.UTF_8));
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Log but continue
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(numThreads, successCount.get());
        }

        @Test
        @DisplayName("should handle concurrent reads of same file")
        void shouldHandleConcurrentReads() throws Exception {
            // Setup: Create file first
            storage.writeAllBytes(FileStorage.Scope.USER, "testuser", "shared.txt",
                    "shared content".getBytes(StandardCharsets.UTF_8));

            int numThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        byte[] content = storage.readAllBytes(
                                FileStorage.Scope.USER, "testuser", "shared.txt");
                        if (new String(content, StandardCharsets.UTF_8).equals("shared content")) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Log but continue
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(numThreads, successCount.get());
        }
    }

    @Nested
    @DisplayName("Filename Sanitization")
    class FilenameSanitizationTests {

        @Test
        @DisplayName("should remove path separators")
        void shouldRemovePathSeparators() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", "path/to/file.txt");

            assertFalse(handle.actualFilename().contains("/"));
        }

        @Test
        @DisplayName("should remove parent directory references")
        void shouldRemoveParentDirectoryReferences() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", "../../secret.txt");

            assertFalse(handle.actualFilename().contains(".."));
        }

        @Test
        @DisplayName("should remove leading dots")
        void shouldRemoveLeadingDots() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", ".hidden");

            assertFalse(handle.actualFilename().startsWith("."));
        }

        @Test
        @DisplayName("should truncate long filenames")
        void shouldTruncateLongFilenames() throws IOException {
            String longName = "a".repeat(100) + ".txt";
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", longName);

            assertTrue(handle.actualFilename().length() <= 64);
        }

        @Test
        @DisplayName("should generate fallback for empty filename")
        void shouldGenerateFallbackForEmptyFilename() throws IOException {
            WriteHandle handle = storage.write(FileStorage.Scope.USER, "testuser", "");

            assertFalse(handle.actualFilename().isEmpty());
            assertTrue(handle.actualFilename().contains("file_"));
        }
    }
}
