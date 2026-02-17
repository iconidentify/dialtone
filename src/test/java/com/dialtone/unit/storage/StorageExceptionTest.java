/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.storage;

import com.dialtone.storage.StorageException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StorageException hierarchy.
 */
@DisplayName("StorageException")
class StorageExceptionTest {

    @Nested
    @DisplayName("StorageException (base)")
    class BaseExceptionTests {

        @Test
        @DisplayName("should create exception with message")
        void shouldCreateExceptionWithMessage() {
            StorageException ex = new StorageException("Test error");

            assertEquals("Test error", ex.getMessage());
        }

        @Test
        @DisplayName("should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            RuntimeException cause = new RuntimeException("Root cause");
            StorageException ex = new StorageException("Test error", cause);

            assertEquals("Test error", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("FileNotFoundException")
    class FileNotFoundExceptionTests {

        @Test
        @DisplayName("should create with filename")
        void shouldCreateWithFilename() {
            StorageException.FileNotFoundException ex =
                    new StorageException.FileNotFoundException("test.txt");

            assertTrue(ex.getMessage().contains("test.txt"));
            assertEquals("test.txt", ex.getFilename());
        }

        @Test
        @DisplayName("should create with scope and owner")
        void shouldCreateWithScopeAndOwner() {
            StorageException.FileNotFoundException ex =
                    new StorageException.FileNotFoundException(
                            "test.txt", "USER", "testuser");

            assertTrue(ex.getMessage().contains("USER"));
            assertTrue(ex.getMessage().contains("testuser"));
            assertTrue(ex.getMessage().contains("test.txt"));
            assertEquals("test.txt", ex.getFilename());
        }

        @Test
        @DisplayName("should return filename from getter")
        void shouldReturnFilenameFromGetter() {
            StorageException.FileNotFoundException ex =
                    new StorageException.FileNotFoundException("myfile.pdf");

            assertEquals("myfile.pdf", ex.getFilename());
        }
    }

    @Nested
    @DisplayName("ReadOnlyStorageException")
    class ReadOnlyStorageExceptionTests {

        @Test
        @DisplayName("should create with operation name")
        void shouldCreateWithOperationName() {
            StorageException.ReadOnlyStorageException ex =
                    new StorageException.ReadOnlyStorageException("write");

            assertTrue(ex.getMessage().contains("write"));
            assertTrue(ex.getMessage().toLowerCase().contains("read-only"));
        }
    }

    @Nested
    @DisplayName("FileSizeLimitExceededException")
    class FileSizeLimitExceededExceptionTests {

        @Test
        @DisplayName("should create with sizes")
        void shouldCreateWithSizes() {
            StorageException.FileSizeLimitExceededException ex =
                    new StorageException.FileSizeLimitExceededException(200, 100);

            assertTrue(ex.getMessage().contains("200"));
            assertTrue(ex.getMessage().contains("100"));
        }

        @Test
        @DisplayName("should return actual size from getter")
        void shouldReturnActualSizeFromGetter() {
            StorageException.FileSizeLimitExceededException ex =
                    new StorageException.FileSizeLimitExceededException(200, 100);

            assertEquals(200, ex.getActualSize());
        }

        @Test
        @DisplayName("should return max size from getter")
        void shouldReturnMaxSizeFromGetter() {
            StorageException.FileSizeLimitExceededException ex =
                    new StorageException.FileSizeLimitExceededException(200, 100);

            assertEquals(100, ex.getMaxSize());
        }
    }

    @Nested
    @DisplayName("StorageInitializationException")
    class StorageInitializationExceptionTests {

        @Test
        @DisplayName("should create with message and cause")
        void shouldCreateWithMessageAndCause() {
            Exception cause = new RuntimeException("Init failed");
            StorageException.StorageInitializationException ex =
                    new StorageException.StorageInitializationException("Failed to init", cause);

            assertTrue(ex.getMessage().contains("Failed to init"));
            assertEquals(cause, ex.getCause());
        }
    }
}
