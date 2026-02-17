/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.spi;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.spi.impl.NativeFdoCompilationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for FdoCompilationService implementations.
 *
 * <p>These tests verify that the native atomforge-fdo backend conforms to the
 * expected behavior contract.</p>
 */
@DisplayName("FDO Compilation Service Contract Tests")
class FdoCompilationServiceContractTest {

    private static final String MINIMAL_FDO = """
        uni_start_stream <00x>
        uni_end_stream
        """;

    // Use minimal FDO that works with the native library's atom registry
    private static final String SIMPLE_FDO = MINIMAL_FDO;

    @Nested
    @DisplayName("Native Java Backend Tests")
    class NativeBackendTests {

        private final FdoCompilationService service = new NativeFdoCompilationService();

        @Test
        @DisplayName("should return correct backend name")
        void shouldReturnCorrectBackendName() {
            assertEquals("java", service.getBackendName());
        }

        @Test
        @DisplayName("should compile minimal FDO source to binary")
        void shouldCompileMinimalFdoToBinary() throws FdoCompilationException {
            byte[] binary = service.compile(MINIMAL_FDO);

            assertNotNull(binary);
            assertTrue(binary.length > 0, "Binary output should not be empty");
        }

        @Test
        @DisplayName("should compile FDO source to chunks")
        void shouldCompileFdoToChunks() throws FdoCompilationException {
            List<FdoChunk> chunks = service.compileToChunks(SIMPLE_FDO, "AT", 0);

            assertNotNull(chunks);
            assertFalse(chunks.isEmpty(), "Should produce at least one chunk");

            // Verify each chunk has valid structure
            for (FdoChunk chunk : chunks) {
                assertNotNull(chunk.getHex(), "Chunk should have hex data");
                assertTrue(chunk.getSize() > 0, "Chunk size should be positive");
                byte[] binary = chunk.getBinaryData();
                assertNotNull(binary, "Chunk should produce binary data");
            }
        }

        @Test
        @DisplayName("should prepend token bytes to chunks")
        void shouldPrependTokenBytesToChunks() throws FdoCompilationException {
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", 0);

            assertFalse(chunks.isEmpty());

            // First chunk should start with token bytes (0x41 0x54 for "AT")
            FdoChunk firstChunk = chunks.get(0);
            byte[] binary = firstChunk.getBinaryData();

            assertTrue(binary.length >= 2, "Chunk should have at least token bytes");
            assertEquals(0x41, binary[0] & 0xFF, "First byte should be 'A' (0x41)");
            assertEquals(0x54, binary[1] & 0xFF, "Second byte should be 'T' (0x54)");
        }

        @Test
        @DisplayName("should tag chunks with stream ID when non-zero")
        void shouldTagChunksWithStreamId() throws FdoCompilationException {
            int streamId = 0x2100;
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", streamId);

            assertFalse(chunks.isEmpty());

            for (FdoChunk chunk : chunks) {
                assertTrue(chunk.hasStreamId(), "Chunk should have stream ID");
                assertEquals(streamId, chunk.getStreamId(), "Stream ID should match");
            }
        }

        @Test
        @DisplayName("should not tag chunks with stream ID when zero")
        void shouldNotTagChunksWithStreamIdWhenZero() throws FdoCompilationException {
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", 0);

            assertFalse(chunks.isEmpty());

            for (FdoChunk chunk : chunks) {
                assertFalse(chunk.hasStreamId(), "Chunk should not have stream ID when passed 0");
            }
        }

        @Test
        @DisplayName("should embed stream ID in chunk payload (little-endian for AT token)")
        void shouldEmbedStreamIdInChunkPayloadLittleEndian() throws FdoCompilationException {
            int streamId = 0x2100;
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", streamId);

            assertFalse(chunks.isEmpty());

            byte[] payload = chunks.get(0).getBinaryData();
            assertTrue(payload.length >= 4, "Payload should have at least 4 bytes (token + stream ID)");

            // Token at bytes 0-1: 'A' 'T'
            assertEquals(0x41, payload[0] & 0xFF, "First byte should be 'A' (0x41)");
            assertEquals(0x54, payload[1] & 0xFF, "Second byte should be 'T' (0x54)");

            // Stream ID at bytes 2-3: 0x2100 in LITTLE-ENDIAN = 00 21 (LSB first)
            assertEquals(0x00, payload[2] & 0xFF, "Stream ID low byte should be 0x00");
            assertEquals(0x21, payload[3] & 0xFF, "Stream ID high byte should be 0x21");
        }

        @Test
        @DisplayName("should use 4-byte stream ID for lowercase token (little-endian)")
        void shouldUse4ByteStreamIdForLowercaseTokenLittleEndian() throws FdoCompilationException {
            int streamId = 0x12345678;
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "at", streamId);

            assertFalse(chunks.isEmpty());

            byte[] payload = chunks.get(0).getBinaryData();
            assertTrue(payload.length >= 6, "Payload should have at least 6 bytes (token + 4-byte stream ID)");

            // Token at bytes 0-1: 'a' 't'
            assertEquals(0x61, payload[0] & 0xFF, "First byte should be 'a' (0x61)");
            assertEquals(0x74, payload[1] & 0xFF, "Second byte should be 't' (0x74)");

            // Stream ID at bytes 2-5: 0x12345678 in LITTLE-ENDIAN = 78 56 34 12 (LSB first)
            assertEquals(0x78, payload[2] & 0xFF, "Stream ID byte 0 (LSB) should be 0x78");
            assertEquals(0x56, payload[3] & 0xFF, "Stream ID byte 1 should be 0x56");
            assertEquals(0x34, payload[4] & 0xFF, "Stream ID byte 2 should be 0x34");
            assertEquals(0x12, payload[5] & 0xFF, "Stream ID byte 3 (MSB) should be 0x12");
        }

        @Test
        @DisplayName("should embed zero stream ID correctly")
        void shouldEmbedZeroStreamIdCorrectly() throws FdoCompilationException {
            // Even when streamId is 0, it should still be embedded in the payload
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", 0);

            assertFalse(chunks.isEmpty());

            byte[] payload = chunks.get(0).getBinaryData();
            assertTrue(payload.length >= 4, "Payload should have at least 4 bytes");

            // Stream ID at bytes 2-3: 0x0000 in LITTLE-ENDIAN = 00 00
            assertEquals(0x00, payload[2] & 0xFF, "Stream ID low byte should be 0x00");
            assertEquals(0x00, payload[3] & 0xFF, "Stream ID high byte should be 0x00");
        }

        @Test
        @DisplayName("should enforce maximum chunk payload size")
        void shouldEnforceMaxChunkPayloadSize() throws FdoCompilationException {
            // Compile the minimal FDO and verify chunks are within size limit
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "AT", 0);

            // Max P3 payload is 119 bytes + 2 token bytes = 121 bytes max per chunk
            for (FdoChunk chunk : chunks) {
                assertTrue(chunk.getSize() <= 121,
                    "Chunk size " + chunk.getSize() + " exceeds max allowed 121 bytes");
            }
        }

        @Test
        @DisplayName("should throw exception for invalid FDO syntax")
        void shouldThrowExceptionForInvalidSyntax() {
            String invalidFdo = "this is not valid FDO syntax!!!";

            assertThrows(FdoCompilationException.class, () -> {
                service.compile(invalidFdo);
            });
        }

        @Test
        @DisplayName("should report healthy when operational")
        void shouldReportHealthyWhenOperational() {
            assertTrue(service.isHealthy(), "Native backend should be healthy");
        }

        @Test
        @DisplayName("should handle different tokens")
        void shouldHandleDifferentTokens() throws FdoCompilationException {
            // Test with lowercase token
            List<FdoChunk> chunks = service.compileToChunks(MINIMAL_FDO, "at", 0);
            assertFalse(chunks.isEmpty());

            byte[] binary = chunks.get(0).getBinaryData();
            assertEquals(0x61, binary[0] & 0xFF, "First byte should be 'a' (0x61)");
            assertEquals(0x74, binary[1] & 0xFF, "Second byte should be 't' (0x74)");
        }
    }

    @Nested
    @DisplayName("Service Factory Tests")
    class ServiceFactoryTests {

        @Test
        @DisplayName("should create native backend when configured")
        void shouldCreateNativeBackendWhenConfigured() {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("fdo.compiler.backend", "java");

            FdoCompilationService service = FdoServiceFactory.createCompilationService(props);

            assertEquals("java", service.getBackendName());
        }

        @Test
        @DisplayName("should create native backend with native alias")
        void shouldCreateNativeBackendWithNativeAlias() {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("fdo.compiler.backend", "native");

            FdoCompilationService service = FdoServiceFactory.createCompilationService(props);

            assertEquals("java", service.getBackendName());
        }

        @Test
        @DisplayName("should throw exception for unknown backend")
        void shouldThrowExceptionForUnknownBackend() {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("fdo.compiler.backend", "unknown");

            assertThrows(IllegalArgumentException.class, () -> {
                FdoServiceFactory.createCompilationService(props);
            });
        }

        @Test
        @DisplayName("should default to java backend")
        void shouldDefaultToJavaBackend() {
            java.util.Properties props = new java.util.Properties();
            // No backend specified

            FdoCompilationService service = FdoServiceFactory.createCompilationService(props);

            assertEquals("java", service.getBackendName());
        }
    }
}
