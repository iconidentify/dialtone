/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.storage;

import com.dialtone.storage.FileMetadata;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageException;
import com.dialtone.storage.impl.ClasspathStorage;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClasspathStorage implementation.
 * Uses the bundled downloads/ resources for testing.
 */
@DisplayName("ClasspathStorage")
class ClasspathStorageTest {

    // Use existing bundled file for testing
    private static final String TEST_FILE = "sample.txt";

    private ClasspathStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        // Use default downloads/ prefix which has bundled files
        storage = new ClasspathStorage("downloads/");
        storage.initialize();
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should use default prefix when none provided")
        void shouldUseDefaultPrefixWhenNone() throws IOException {
            ClasspathStorage defaultStorage = new ClasspathStorage();
            defaultStorage.initialize();
            assertNotNull(defaultStorage);
            // Should find bundled file with default downloads/ prefix
            assertTrue(defaultStorage.exists(FileStorage.Scope.GLOBAL, null, TEST_FILE));
        }

        @Test
        @DisplayName("should use custom prefix")
        void shouldUseCustomPrefix() throws IOException {
            ClasspathStorage customStorage = new ClasspathStorage("downloads/");
            customStorage.initialize();
            assertTrue(customStorage.exists(FileStorage.Scope.GLOBAL, null, TEST_FILE));
        }

        @Test
        @DisplayName("should report not writable")
        void shouldReportNotWritable() {
            assertFalse(storage.isWritable());
        }

        @Test
        @DisplayName("should return max long for max file size")
        void shouldReturnMaxLongForMaxFileSize() {
            assertEquals(Long.MAX_VALUE, storage.getMaxFileSizeBytes());
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("should return true for existing global resource")
        void shouldReturnTrueForExistingGlobalResource() {
            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, TEST_FILE));
        }

        @Test
        @DisplayName("should return false for non-existent resource")
        void shouldReturnFalseForNonExistentResource() {
            assertFalse(storage.exists(FileStorage.Scope.GLOBAL, null, "nonexistent.txt"));
        }

        @Test
        @DisplayName("should return false for user scope")
        void shouldReturnFalseForUserScope() {
            assertFalse(storage.exists(FileStorage.Scope.USER, "testuser", TEST_FILE));
        }

        @Test
        @DisplayName("should sanitize filename and reject path traversal")
        void shouldSanitizeFilename() {
            // Path traversal should be sanitized
            assertFalse(storage.exists(FileStorage.Scope.GLOBAL, null, "../../../etc/passwd"));
        }
    }

    @Nested
    @DisplayName("getMetadata()")
    class GetMetadataTests {

        @Test
        @DisplayName("should return metadata for existing resource")
        void shouldReturnMetadataForExistingResource() {
            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.GLOBAL, null, TEST_FILE);

            assertTrue(metadata.isPresent());
            assertEquals(TEST_FILE, metadata.get().filename());
            assertTrue(metadata.get().sizeBytes() > 0);
        }

        @Test
        @DisplayName("should return empty for non-existent resource")
        void shouldReturnEmptyForNonExistentResource() {
            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.GLOBAL, null, "nonexistent.txt");

            assertTrue(metadata.isEmpty());
        }

        @Test
        @DisplayName("should return empty for user scope")
        void shouldReturnEmptyForUserScope() {
            Optional<FileMetadata> metadata = storage.getMetadata(
                    FileStorage.Scope.USER, "testuser", TEST_FILE);

            assertTrue(metadata.isEmpty());
        }
    }

    @Nested
    @DisplayName("read()")
    class ReadTests {

        @Test
        @DisplayName("should read existing resource")
        void shouldReadExistingResource() throws IOException {
            try (InputStream is = storage.read(FileStorage.Scope.GLOBAL, null, TEST_FILE)) {
                byte[] content = is.readAllBytes();
                assertTrue(content.length > 0, "File should have content");
            }
        }

        @Test
        @DisplayName("should throw FileNotFoundException for missing resource")
        void shouldThrowFileNotFoundForMissingResource() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.read(FileStorage.Scope.GLOBAL, null, "nonexistent.txt"));
        }

        @Test
        @DisplayName("should throw FileNotFoundException for user scope")
        void shouldThrowFileNotFoundForUserScope() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.read(FileStorage.Scope.USER, "testuser", TEST_FILE));
        }
    }

    @Nested
    @DisplayName("readAllBytes()")
    class ReadAllBytesTests {

        @Test
        @DisplayName("should read all bytes from resource")
        void shouldReadAllBytesFromResource() throws IOException {
            byte[] content = storage.readAllBytes(FileStorage.Scope.GLOBAL, null, TEST_FILE);

            assertTrue(content.length > 0, "File should have content");
        }

        @Test
        @DisplayName("should throw FileNotFoundException for missing resource")
        void shouldThrowFileNotFoundForMissingResource() {
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.readAllBytes(FileStorage.Scope.GLOBAL, null, "nonexistent.txt"));
        }
    }

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("should return empty list (classpath cannot be enumerated)")
        void shouldReturnEmptyList() {
            List<FileMetadata> files = storage.list(FileStorage.Scope.GLOBAL, null);
            assertTrue(files.isEmpty());
        }
    }

    @Nested
    @DisplayName("Write Operations (Should Throw)")
    class WriteOperationTests {

        @Test
        @DisplayName("should throw ReadOnlyStorageException on write")
        void shouldThrowReadOnlyExceptionOnWrite() {
            assertThrows(StorageException.ReadOnlyStorageException.class, () ->
                    storage.write(FileStorage.Scope.GLOBAL, null, "newfile.txt"));
        }

        @Test
        @DisplayName("should throw ReadOnlyStorageException on writeAllBytes")
        void shouldThrowReadOnlyExceptionOnWriteAllBytes() {
            assertThrows(StorageException.ReadOnlyStorageException.class, () ->
                    storage.writeAllBytes(FileStorage.Scope.GLOBAL, null, "newfile.txt",
                            "content".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("should throw ReadOnlyStorageException on delete")
        void shouldThrowReadOnlyExceptionOnDelete() {
            assertThrows(StorageException.ReadOnlyStorageException.class, () ->
                    storage.delete(FileStorage.Scope.GLOBAL, null, TEST_FILE));
        }

        @Test
        @DisplayName("should throw ReadOnlyStorageException on setMetadata with single key")
        void shouldThrowReadOnlyExceptionOnSetMetadataSingleKey() {
            assertThrows(StorageException.ReadOnlyStorageException.class, () ->
                    storage.setMetadata(FileStorage.Scope.GLOBAL, null, TEST_FILE,
                            "key", "value"));
        }

        @Test
        @DisplayName("should throw ReadOnlyStorageException on setMetadata with map")
        void shouldThrowReadOnlyExceptionOnSetMetadataMap() {
            assertThrows(StorageException.ReadOnlyStorageException.class, () ->
                    storage.setMetadata(FileStorage.Scope.GLOBAL, null, TEST_FILE,
                            Map.of("key", "value")));
        }
    }

    @Nested
    @DisplayName("getStorageUsed()")
    class GetStorageUsedTests {

        @Test
        @DisplayName("should return zero")
        void shouldReturnZero() {
            assertEquals(0, storage.getStorageUsed(FileStorage.Scope.GLOBAL, null));
        }
    }

    @Nested
    @DisplayName("Filename Sanitization")
    class FilenameSanitizationTests {

        @Test
        @DisplayName("should prevent path traversal on exists")
        void shouldPreventPathTraversalOnExists() {
            // Should sanitize ../../../ and not find the file
            assertFalse(storage.exists(FileStorage.Scope.GLOBAL, null, "../test-file.txt"));
        }

        @Test
        @DisplayName("should prevent path traversal on read")
        void shouldPreventPathTraversalOnRead() {
            // Should sanitize and not find the file with traversal
            assertThrows(StorageException.FileNotFoundException.class, () ->
                    storage.read(FileStorage.Scope.GLOBAL, null, "../../test-file.txt"));
        }
    }
}
