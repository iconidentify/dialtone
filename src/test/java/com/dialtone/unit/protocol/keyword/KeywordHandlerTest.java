/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.keyword;

import com.dialtone.art.ArtService;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.keyword.handlers.*;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KeywordHandler implementations.
 * Tests keyword/description getters and constructor validation.
 */
@DisplayName("KeywordHandlers")
class KeywordHandlerTest {

    @Nested
    @DisplayName("HelloWorldKeywordHandler")
    class HelloWorldTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            XferService mockXferService = mock(XferService.class);
            HelloWorldKeywordHandler handler = new HelloWorldKeywordHandler(mockXferService);
            assertEquals("hello world", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            XferService mockXferService = mock(XferService.class);
            HelloWorldKeywordHandler handler = new HelloWorldKeywordHandler(mockXferService);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null XferService")
        void shouldThrowOnNullXferService() {
            assertThrows(IllegalArgumentException.class,
                () -> new HelloWorldKeywordHandler(null));
        }
    }

    @Nested
    @DisplayName("TosKeywordHandler")
    class TosTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            TosKeywordHandler handler = new TosKeywordHandler(mockCompiler, new Properties(), null);
            assertEquals("tos", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            TosKeywordHandler handler = new TosKeywordHandler(mockCompiler, new Properties(), null);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null FdoCompiler")
        void shouldThrowOnNullCompiler() {
            assertThrows(IllegalArgumentException.class,
                () -> new TosKeywordHandler(null, new Properties(), null));
        }
    }

    @Nested
    @DisplayName("InvokeKeywordHandler")
    class InvokeTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            InvokeKeywordHandler handler = new InvokeKeywordHandler(mockCompiler);
            assertEquals("invoke", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            InvokeKeywordHandler handler = new InvokeKeywordHandler(mockCompiler);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null FdoCompiler")
        void shouldThrowOnNullCompiler() {
            assertThrows(IllegalArgumentException.class,
                () -> new InvokeKeywordHandler(null));
        }
    }

    @Nested
    @DisplayName("ServerLogsKeywordHandler")
    class ServerLogsTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            ServerLogsKeywordHandler handler = new ServerLogsKeywordHandler(mockCompiler);
            assertEquals("server logs", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            ServerLogsKeywordHandler handler = new ServerLogsKeywordHandler(mockCompiler);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null FdoCompiler")
        void shouldThrowOnNullCompiler() {
            assertThrows(IllegalArgumentException.class,
                () -> new ServerLogsKeywordHandler(null));
        }
    }

    @Nested
    @DisplayName("ImageViewerKeywordHandler")
    class ImageViewerTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            ArtService mockArtService = mock(ArtService.class);
            ImageViewerKeywordHandler handler = new ImageViewerKeywordHandler(mockCompiler, mockArtService);
            assertEquals("art", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            ArtService mockArtService = mock(ArtService.class);
            ImageViewerKeywordHandler handler = new ImageViewerKeywordHandler(mockCompiler, mockArtService);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null FdoCompiler")
        void shouldThrowOnNullCompiler() {
            ArtService mockArtService = mock(ArtService.class);
            assertThrows(IllegalArgumentException.class,
                () -> new ImageViewerKeywordHandler(null, mockArtService));
        }
    }

    @Nested
    @DisplayName("DownloadKeywordHandler")
    class DownloadTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            XferService mockXferService = mock(XferService.class);
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            FileStorage mockStorage = mock(FileStorage.class);
            DownloadKeywordHandler handler = new DownloadKeywordHandler(mockXferService, mockCompiler, mockStorage);
            assertEquals("download", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            XferService mockXferService = mock(XferService.class);
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            FileStorage mockStorage = mock(FileStorage.class);
            DownloadKeywordHandler handler = new DownloadKeywordHandler(mockXferService, mockCompiler, mockStorage);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null XferService")
        void shouldThrowOnNullXferService() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            FileStorage mockStorage = mock(FileStorage.class);
            assertThrows(IllegalArgumentException.class,
                () -> new DownloadKeywordHandler(null, mockCompiler, mockStorage));
        }
    }

    @Nested
    @DisplayName("UploadKeywordHandler")
    class UploadTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            XferUploadService mockUploadService = mock(XferUploadService.class);
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            UploadKeywordHandler handler = new UploadKeywordHandler(mockUploadService, mockCompiler);
            assertEquals("upload", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            XferUploadService mockUploadService = mock(XferUploadService.class);
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            UploadKeywordHandler handler = new UploadKeywordHandler(mockUploadService, mockCompiler);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should throw on null XferUploadService")
        void shouldThrowOnNullUploadService() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            assertThrows(IllegalArgumentException.class,
                () -> new UploadKeywordHandler(null, mockCompiler));
        }
    }

    @Nested
    @DisplayName("SkalholtKeywordHandler")
    class SkalholtTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            SkalholtKeywordHandler handler = new SkalholtKeywordHandler(mockCompiler);
            assertEquals("skalholt", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            SkalholtKeywordHandler handler = new SkalholtKeywordHandler(mockCompiler);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
        }

        @Test
        @DisplayName("should handle null FdoCompiler gracefully")
        void shouldHandleNullCompiler() {
            // SkalholtKeywordHandler doesn't validate null - just verify it doesn't throw
            assertDoesNotThrow(() -> new SkalholtKeywordHandler(null));
        }
    }

    @Nested
    @DisplayName("PieterKeywordHandler")
    class PieterTests {

        @Test
        @DisplayName("should return correct keyword")
        void shouldReturnCorrectKeyword() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            PieterKeywordHandler handler = new PieterKeywordHandler(mockCompiler);
            assertEquals("pieter", handler.getKeyword());
        }

        @Test
        @DisplayName("should return non-empty description")
        void shouldReturnDescription() {
            FdoCompiler mockCompiler = mock(FdoCompiler.class);
            PieterKeywordHandler handler = new PieterKeywordHandler(mockCompiler);
            assertNotNull(handler.getDescription());
            assertFalse(handler.getDescription().isEmpty());
            assertTrue(handler.getDescription().toLowerCase().contains("pieter"),
                "Description should mention Pieter");
        }

        @Test
        @DisplayName("should throw on null FdoCompiler")
        void shouldThrowOnNullCompiler() {
            assertThrows(IllegalArgumentException.class,
                () -> new PieterKeywordHandler(null));
        }
    }
}
