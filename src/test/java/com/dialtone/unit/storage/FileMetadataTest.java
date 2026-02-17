/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.storage;

import com.dialtone.storage.FileMetadata;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileMetadata record.
 */
@DisplayName("FileMetadata")
class FileMetadataTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("should create metadata with minimal fields")
        void shouldCreateMetadataWithMinimalFields() {
            Instant now = Instant.now();
            FileMetadata meta = new FileMetadata("test.txt", 100, now);

            assertEquals("test.txt", meta.filename());
            assertEquals(100, meta.sizeBytes());
            assertEquals(now, meta.modifiedAt());
            assertEquals(now, meta.createdAt()); // Should default to modifiedAt
            assertNull(meta.contentType());
            assertTrue(meta.customMetadata().isEmpty());
            assertNull(meta.path());
        }

        @Test
        @DisplayName("should create metadata with path")
        void shouldCreateMetadataWithPath() {
            Instant now = Instant.now();
            Path path = Path.of("/tmp/test.txt");
            FileMetadata meta = new FileMetadata("test.txt", 100, now, path);

            assertEquals("test.txt", meta.filename());
            assertEquals(path, meta.path());
        }

        @Test
        @DisplayName("should create metadata without path")
        void shouldCreateMetadataWithoutPath() {
            Instant now = Instant.now();
            FileMetadata meta = new FileMetadata("test.txt", 100, now, now,
                    "text/plain", Map.of("key", "value"));

            assertEquals("test.txt", meta.filename());
            assertEquals("text/plain", meta.contentType());
            assertEquals("value", meta.customMetadata().get("key"));
            assertNull(meta.path());
        }

        @Test
        @DisplayName("should create metadata with all fields")
        void shouldCreateMetadataWithAllFields() {
            Instant created = Instant.now().minusSeconds(3600);
            Instant modified = Instant.now();
            Path path = Path.of("/tmp/test.txt");
            Map<String, String> custom = Map.of("source", "upload");

            FileMetadata meta = new FileMetadata("test.txt", 100, created, modified,
                    "text/plain", custom, path);

            assertEquals("test.txt", meta.filename());
            assertEquals(100, meta.sizeBytes());
            assertEquals(created, meta.createdAt());
            assertEquals(modified, meta.modifiedAt());
            assertEquals("text/plain", meta.contentType());
            assertEquals(custom, meta.customMetadata());
            assertEquals(path, meta.path());
        }
    }

    @Nested
    @DisplayName("getSizeFormatted()")
    class GetSizeFormattedTests {

        @Test
        @DisplayName("should format byte size for small files")
        void shouldFormatByteSizeForSmallFiles() {
            FileMetadata meta = new FileMetadata("test.txt", 500, Instant.now());
            assertEquals("500 B", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should format kilobyte size for medium files")
        void shouldFormatKilobyteSizeForMediumFiles() {
            FileMetadata meta = new FileMetadata("test.txt", 5120, Instant.now()); // 5 KB
            assertEquals("5.0 KB", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should format megabyte size for large files")
        void shouldFormatMegabyteSizeForLargeFiles() {
            FileMetadata meta = new FileMetadata("test.txt", 5 * 1024 * 1024, Instant.now()); // 5 MB
            assertEquals("5.0 MB", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should format edge case at 1024 bytes")
        void shouldFormatEdgeCaseAt1024Bytes() {
            FileMetadata meta = new FileMetadata("test.txt", 1024, Instant.now());
            assertEquals("1.0 KB", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should format edge case at 1MB")
        void shouldFormatEdgeCaseAt1MB() {
            FileMetadata meta = new FileMetadata("test.txt", 1024 * 1024, Instant.now());
            assertEquals("1.0 MB", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should handle zero size")
        void shouldHandleZeroSize() {
            FileMetadata meta = new FileMetadata("test.txt", 0, Instant.now());
            assertEquals("0 B", meta.getSizeFormatted());
        }

        @Test
        @DisplayName("should format fractional kilobytes")
        void shouldFormatFractionalKilobytes() {
            FileMetadata meta = new FileMetadata("test.txt", 2560, Instant.now()); // 2.5 KB
            assertEquals("2.5 KB", meta.getSizeFormatted());
        }
    }

    @Nested
    @DisplayName("Custom Metadata")
    class CustomMetadataTests {

        @Test
        @DisplayName("should get custom metadata value")
        void shouldGetCustomMetadataValue() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now(), Instant.now(),
                    null, Map.of("source", "upload"));

            assertEquals("upload", meta.getCustom("source"));
        }

        @Test
        @DisplayName("should return null for missing custom key")
        void shouldReturnNullForMissingCustomKey() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now(), Instant.now(),
                    null, Map.of("source", "upload"));

            assertNull(meta.getCustom("nonexistent"));
        }

        @Test
        @DisplayName("should return null when custom metadata map is empty")
        void shouldReturnNullWhenCustomMetadataMapIsEmpty() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now());

            assertNull(meta.getCustom("anykey"));
        }

        @Test
        @DisplayName("should check custom key exists")
        void shouldCheckCustomKeyExists() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now(), Instant.now(),
                    null, Map.of("source", "upload"));

            assertTrue(meta.hasCustom("source"));
        }

        @Test
        @DisplayName("should check custom key does not exist")
        void shouldCheckCustomKeyDoesNotExist() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now(), Instant.now(),
                    null, Map.of("source", "upload"));

            assertFalse(meta.hasCustom("nonexistent"));
        }

        @Test
        @DisplayName("should handle empty map in hasCustom")
        void shouldHandleEmptyMapInHasCustom() {
            FileMetadata meta = new FileMetadata("test.txt", 100, Instant.now());

            assertFalse(meta.hasCustom("anykey"));
        }
    }

    @Nested
    @DisplayName("getLastModifiedMs()")
    class GetLastModifiedMsTests {

        @Test
        @DisplayName("should return epoch millis for valid timestamp")
        void shouldReturnEpochMillisForValidTimestamp() {
            Instant now = Instant.now();
            FileMetadata meta = new FileMetadata("test.txt", 100, now);

            assertEquals(now.toEpochMilli(), meta.getLastModifiedMs());
        }

        @Test
        @DisplayName("should return zero for null modifiedAt")
        void shouldReturnZeroForNullModifiedAt() {
            // Create with null modifiedAt using full constructor
            FileMetadata meta = new FileMetadata("test.txt", 100, null, null,
                    null, Collections.emptyMap(), null);

            assertEquals(0, meta.getLastModifiedMs());
        }
    }
}
