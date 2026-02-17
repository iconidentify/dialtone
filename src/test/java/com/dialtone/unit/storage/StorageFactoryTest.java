/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.storage;

import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageFactory;
import com.dialtone.storage.impl.ClasspathStorage;
import com.dialtone.storage.impl.LocalFileSystemStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StorageFactory.
 */
@DisplayName("StorageFactory")
class StorageFactoryTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create local storage by default")
        void shouldCreateLocalStorageByDefault() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());

            FileStorage storage = StorageFactory.create(props);

            assertNotNull(storage);
            assertTrue(storage.isWritable());
        }

        @Test
        @DisplayName("should create local storage when explicitly configured")
        void shouldCreateLocalStorageWhenExplicitlyConfigured() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.type", "local");
            props.setProperty("storage.local.base.dir", tempDir.toString());

            FileStorage storage = StorageFactory.create(props);

            assertNotNull(storage);
            assertTrue(storage.isWritable());
        }

        @Test
        @DisplayName("should create classpath storage when configured")
        void shouldCreateClasspathStorageWhenConfigured() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.type", "classpath");

            FileStorage storage = StorageFactory.create(props);

            assertNotNull(storage);
            assertFalse(storage.isWritable());
        }

        @Test
        @DisplayName("should fall back to local for unknown type")
        void shouldFallBackToLocalForUnknownType() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.type", "unknown");
            props.setProperty("storage.local.base.dir", tempDir.toString());

            FileStorage storage = StorageFactory.create(props);

            assertNotNull(storage);
            assertTrue(storage.isWritable());
        }
    }

    @Nested
    @DisplayName("createLocalStorage()")
    class CreateLocalStorageTests {

        @Test
        @DisplayName("should use default base dir")
        void shouldUseDefaultBaseDir() throws IOException {
            Properties props = new Properties();

            LocalFileSystemStorage storage = StorageFactory.createLocalStorage(props);

            assertNotNull(storage);
        }

        @Test
        @DisplayName("should use configured base dir")
        void shouldUseConfiguredBaseDir() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());

            LocalFileSystemStorage storage = StorageFactory.createLocalStorage(props);

            assertNotNull(storage);
        }

        @Test
        @DisplayName("should use default max file size of 100MB")
        void shouldUseDefaultMaxFileSize() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());

            LocalFileSystemStorage storage = StorageFactory.createLocalStorage(props);

            assertEquals(100 * 1024 * 1024, storage.getMaxFileSizeBytes());
        }

        @Test
        @DisplayName("should use configured max file size")
        void shouldUseConfiguredMaxFileSize() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());
            props.setProperty("storage.local.max.file.size.mb", "50");

            LocalFileSystemStorage storage = StorageFactory.createLocalStorage(props);

            assertEquals(50 * 1024 * 1024, storage.getMaxFileSizeBytes());
        }

        @Test
        @DisplayName("should handle invalid max file size gracefully")
        void shouldHandleInvalidMaxFileSize() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());
            props.setProperty("storage.local.max.file.size.mb", "not-a-number");

            LocalFileSystemStorage storage = StorageFactory.createLocalStorage(props);

            // Should fall back to default
            assertEquals(100 * 1024 * 1024, storage.getMaxFileSizeBytes());
        }
    }

    @Nested
    @DisplayName("createClasspathStorage()")
    class CreateClasspathStorageTests {

        @Test
        @DisplayName("should use default prefix")
        void shouldUseDefaultPrefix() throws IOException {
            Properties props = new Properties();

            ClasspathStorage storage = StorageFactory.createClasspathStorage(props);

            assertNotNull(storage);
            assertFalse(storage.isWritable());
            // Should find bundled file with default downloads/ prefix
            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, "sample.txt"));
        }

        @Test
        @DisplayName("should use configured prefix")
        void shouldUseConfiguredPrefix() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.classpath.prefix", "downloads/");

            ClasspathStorage storage = StorageFactory.createClasspathStorage(props);

            assertNotNull(storage);
            // Should find bundled file
            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, "sample.txt"));
        }
    }

    @Nested
    @DisplayName("createWithFallback()")
    class CreateWithFallbackTests {

        @Test
        @DisplayName("should create composite storage")
        void shouldCreateCompositeStorage() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());

            FileStorage storage = StorageFactory.createWithFallback(props);

            assertNotNull(storage);
            // CompositeStorage should be writable (delegates to LocalFileSystemStorage)
            assertTrue(storage.isWritable());
        }

        @Test
        @DisplayName("should find file in local when present")
        void shouldFindFileInLocalWhenPresent() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());

            FileStorage storage = StorageFactory.createWithFallback(props);

            // Write to local storage
            storage.writeAllBytes(FileStorage.Scope.USER, "testuser", "local.txt",
                    "local content".getBytes());

            assertTrue(storage.exists(FileStorage.Scope.USER, "testuser", "local.txt"));
        }

        @Test
        @DisplayName("should fall back to classpath for bundled resources")
        void shouldFallBackToClasspathForBundledResources() throws IOException {
            Properties props = new Properties();
            props.setProperty("storage.local.base.dir", tempDir.toString());
            props.setProperty("storage.classpath.prefix", "downloads/");

            FileStorage storage = StorageFactory.createWithFallback(props);

            // Should find bundled file via classpath fallback
            assertTrue(storage.exists(FileStorage.Scope.GLOBAL, null, "sample.txt"));
        }
    }
}
