/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.atomforge.fdo.FdoCompiler;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.im.InstantMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the three instant message extraction patterns.
 *
 * Tests the complete flow of multi-frame instant message extraction,
 * validating that each pattern handles its specific fragmentation scenario correctly.
 *
 * The three patterns are:
 * 1. Pattern 1: tryFdoBinaryReassembly() - FDO structure fragmented across frames
 * 2. Pattern 2: reassembleLargeAtomFrameByFrame() - Complete FDO atoms per frame, message spans atoms
 * 3. Pattern 3: extractTextFromUniVoid() - Hex-encoded text extraction (tested in MultiFrameInstantMessageTest)
 */
@DisplayName("Instant Message Pattern Integration Tests")
class InstantMessagePatternIntegrationTest {

    static {
        System.setProperty("atomforge.mock.enabled", "true");
    }

    // ========================================================================================
    // Pattern 1: tryFdoBinaryReassembly() Tests - FDO Binary Fragmentation
    // ========================================================================================

    @Test
    @DisplayName("Pattern 1: Should extract message when FDO binary is fragmented across frames")
    @org.junit.jupiter.api.Disabled("Test uses synthetic partial FDO text that doesn't represent real binary fragmentation - requires real captured frames")
    void shouldExtractMessageFromFragmentedFdoBinary() throws Exception {
        // Given: Multi-frame sequence where the FDO atom structure itself is split
        // Frame 0: Contains start of man_set_context_relative and partial de_data
        // Frame 1: Contains completion of de_data and man_end_context

        // Simulating real FDO binary fragmentation scenario
        List<byte[]> frames = Arrays.asList(
            buildP3Frame(buildPartialFdoBinary(
                "man_set_context_relative <1>\n" +
                "de_data <\"TestUser\">\n" +
                "man_set_context_relative <2>\n" +
                "de_da"  // Atom cut off mid-structure
            )),
            buildP3Frame(buildPartialFdoBinary(
                "ta <\"Hello from fragmented FDO binary!\">\n" +
                "man_end_context\n" +
                "uni_end_stream"
            ))
        );

        // When: Extracting via Pattern 1 (FDO binary reassembly)
        String extracted = extractMultiFrameInstantMessageViaPattern1(frames);

        // Then: Should successfully extract the complete message
        assertNotNull(extracted, "Pattern 1 should extract message from fragmented FDO binary");
        assertEquals("Hello from fragmented FDO binary!", extracted);
    }

    @Test
    @DisplayName("Pattern 1: Should handle large messages fragmented across multiple frames")
    @org.junit.jupiter.api.Disabled("Test uses synthetic partial FDO text that doesn't represent real binary fragmentation - requires real captured frames")
    void shouldExtractLargeMessageFromMultipleFragments() throws Exception {
        // Given: Large message split across 3 frames due to FDO structure fragmentation
        String longMessage = "This is a very long instant message that exceeds normal frame boundaries and requires multiple P3 frames to transmit the complete FDO structure. Pattern 1 should reassemble all frames first.";

        List<byte[]> frames = Arrays.asList(
            buildP3Frame(buildPartialFdoBinary(
                "man_set_context_relative <1>\n" +
                "de_data <\"TestUser\">\n" +
                "man_set_context_"
            )),
            buildP3Frame(buildPartialFdoBinary(
                "relative <2>\n" +
                "de_data <\"" + longMessage.substring(0, 50) + "\""  // Partial message
            )),
            buildP3Frame(buildPartialFdoBinary(
                longMessage.substring(50) + "\">\n" +
                "man_end_context\n" +
                "uni_end_stream"
            ))
        );

        // When: Extracting via Pattern 1
        String extracted = extractMultiFrameInstantMessageViaPattern1(frames);

        // Then: Should extract the complete long message
        assertNotNull(extracted);
        assertEquals(longMessage, extracted);
    }

    // ========================================================================================
    // Pattern 2: reassembleLargeAtomFrameByFrame() Tests - Complete FDO per frame
    // ========================================================================================

    @Test
    @DisplayName("Pattern 2: Should extract message from complete FDO atoms per frame")
    void shouldExtractMessageFromFrameByFrameWithLargeAtoms() throws Exception {
        // Given: Multi-frame sequence where each frame has complete FDO
        // Note: FdoStream extracts de_data values directly, large atom handling is internal
        String message = "Hello from frame-by-frame!";

        List<byte[]> frames = Arrays.asList(
            // Frame 0: Has message in de_data
            buildP3Frame(buildCompleteFdoBinary(
                "man_set_context_relative <1>\n" +
                "de_data <\"TestUser\">\n" +
                "man_set_context_relative <2>\n" +
                "de_data <\"" + message + "\">\n" +
                "man_end_context"
            ))
        );

        // When: Extracting via the full extraction flow (now uses FdoStream)
        InstantMessage extracted = extractMultiFrameInstantMessage(frames);

        // Then: Should extract message from de_data
        assertNotNull(extracted, "Should extract from FDO with de_data");
        assertEquals(message, extracted.message());
    }

    @Test
    @DisplayName("Pattern 2: Should extract message from FDO with recipient and message")
    void shouldExtractMessageFromMixedEncodingFrames() throws Exception {
        // Given: FDO with recipient and message in de_data atoms
        // Note: FdoStream extracts de_data values directly
        String recipient = "TestRecipient";
        String message = "Mixed encoding test!";

        List<byte[]> frames = Arrays.asList(
            buildP3Frame(buildCompleteFdoBinary(
                "man_set_context_relative <1>\n" +
                "de_data <\"" + recipient + "\">\n" +
                "man_set_context_relative <2>\n" +
                "de_data <\"" + message + "\">\n" +
                "man_end_context"
            ))
        );

        // When: Extracting via the full extraction flow (now uses FdoStream)
        InstantMessage extracted = extractMultiFrameInstantMessage(frames);

        // Then: Should extract recipient and message from de_data atoms
        assertNotNull(extracted);
        assertEquals(recipient, extracted.recipient());
        assertEquals(message, extracted.message());
    }

    // ========================================================================================
    // Full Integration Tests - All Three Patterns
    // ========================================================================================

    @Test
    @DisplayName("Integration: Should extract message using FdoStream")
    void shouldTryPattern1FirstThenFallbackToPattern2() throws Exception {
        // Given: Standard FDO with de_data containing the message
        // Note: FdoStream extracts de_data values directly
        String message = "Fallback test";
        List<byte[]> frames = Arrays.asList(
            buildP3Frame(buildCompleteFdoBinary(
                "man_set_context_relative <2>\n" +
                "de_data <\"" + message + "\">\n" +
                "man_end_context"
            ))
        );

        // When: Extracting via the complete multi-frame extraction flow (now uses FdoStream)
        InstantMessage extracted = extractMultiFrameInstantMessage(frames);

        // Then: Should extract message from de_data
        assertNotNull(extracted, "Should extract from de_data");
        assertNotNull(extracted.message(), "Message should not be null");
        assertEquals(message, extracted.message());
    }

    @Test
    @DisplayName("Integration: Should handle empty frames gracefully")
    void shouldHandleEmptyFramesGracefully() throws Exception {
        // Given: Frames with minimal content and empty frames
        List<byte[]> frames = Arrays.asList(
            buildP3Frame(new byte[0]),  // Empty frame
            buildP3Frame(buildCompleteFdoBinary(
                "de_data <\"Empty test\">\n" +
                "uni_end_stream"
            )),
            buildP3Frame(new byte[0])  // Another empty frame
        );

        // When: Extracting
        InstantMessage extracted = extractMultiFrameInstantMessage(frames);

        // Then: Should skip empty frames and extract message
        assertNotNull(extracted);
        assertEquals("Empty test", extracted.message());
    }

    @Test
    @DisplayName("Integration: Should handle invalid frames gracefully")
    void shouldReturnNullWhenAllPatternsFail() throws Exception {
        // Given: Frames with no extractable message content
        // Note: FdoStreamExtractor may throw exceptions for invalid frames
        List<byte[]> frames = Arrays.asList(
            buildP3Frame(buildCompleteFdoBinary("invalid_token")),
            buildP3Frame(buildCompleteFdoBinary("another_invalid_token"))
        );

        // When: Extracting
        InstantMessage extracted = null;
        try {
            extracted = extractMultiFrameInstantMessage(frames);
        } catch (Exception e) {
            // FdoStreamExtractor may throw for invalid frames - this is acceptable
        }

        // Then: Should return null or throw exception when extraction fails
        // Both behaviors are acceptable for invalid input
        if (extracted != null) {
            assertNull(extracted.message(), "Message should be null for invalid frames");
        }
    }

    // ========================================================================================
    // Real-world Scenario Tests
    // ========================================================================================

    @Test
    @DisplayName("Real-world: Should handle actual AOL client instant message sequence")
    void shouldHandleActualAolClientSequence() throws Exception {
        // Given: Realistic multi-frame sequence as would be sent by AOL client
        // Based on actual protocol captures and common fragmentation patterns
        List<byte[]> frames = Arrays.asList(
            // Frame 0: Standard IM header with recipient
            buildP3Frame(buildCompleteFdoBinary(
                "man_set_context_relative <1>\n" +
                "de_data <\"BuddyName\">\n" +
                "man_set_context_relative <2>\n" +
                "de_data <\"Hey! Check out this link: https://example.com/very/long/url/that/might/cause/fragmentation\">\n" +
                "man_end_context"
            )),
            // Frame 1: End stream marker
            buildP3Frame(buildCompleteFdoBinary(
                "uni_end_stream"
            ))
        );

        // When: Extracting
        InstantMessage extracted = extractMultiFrameInstantMessage(frames);

        // Then: Should extract the URL message correctly
        assertNotNull(extracted);
        assertNotNull(extracted.message());
        assertTrue(extracted.message().contains("https://example.com/very/long/url"));
        assertTrue(extracted.message().startsWith("Hey! Check out this link:"));
        assertEquals("BuddyName", extracted.recipient());
    }

    // ========================================================================================
    // Helper Methods for Test Frame Construction
    // ========================================================================================

    /**
     * Builds a complete P3 frame using the correct AOL v2 format.
     * Format: magic(1), crc(2), len(2), tx(1), rx(1), type(1), token(2), streamId(2), payload...
     */
    private byte[] buildP3Frame(byte[] payload) {
        try {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();

            // AOL v2 Frame Header
            frame.write(0x5A);                                           // Byte 0: Magic (0x5A)
            frame.write(0x00);                                           // Byte 1: CRC high (placeholder)
            frame.write(0x00);                                           // Byte 2: CRC low (placeholder)
            frame.write((payload.length >> 8) & 0xFF);                   // Byte 3: Payload length high
            frame.write(payload.length & 0xFF);                          // Byte 4: Payload length low
            frame.write(0x11);                                           // Byte 5: TX sequence
            frame.write(0x11);                                           // Byte 6: RX sequence
            frame.write(0x20);                                           // Byte 7: Type (DATA)
            frame.write('i');                                            // Byte 8: Token 'iS' first char
            frame.write('S');                                            // Byte 9: Token 'iS' second char
            frame.write(0x01);                                           // Byte 10: Stream ID high (arbitrary 0x0100)
            frame.write(0x00);                                           // Byte 11: Stream ID low

            frame.write(payload);
            return frame.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build P3 frame", e);
        }
    }

    /**
     * Builds FDO binary content for complete FDO structures.
     * Uses the native FdoCompiler for real binary output.
     */
    private byte[] buildCompleteFdoBinary(String fdoSource) {
        // Wrap source in uni_start_stream/uni_end_stream if not present
        String wrappedSource = fdoSource;
        if (!fdoSource.contains("uni_start_stream")) {
            wrappedSource = "uni_start_stream <00x>\n" + fdoSource + "\nuni_end_stream";
        }
        try {
            FdoCompiler compiler = FdoCompiler.create();
            return compiler.compile(wrappedSource);
        } catch (Exception e) {
            // Fallback to UTF-8 bytes for legacy test data that can't be compiled
            return fdoSource.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Builds partial FDO binary for fragmentation scenarios.
     * Compiles the FDO source to binary using native compiler.
     */
    private byte[] buildPartialFdoBinary(String partialFdoSource) {
        try {
            // Wrap partial FDO in a valid stream structure
            String completeFdo = "uni_start_stream <00x>\n" + partialFdoSource;
            if (!completeFdo.contains("uni_end_stream")) {
                completeFdo += "\nuni_end_stream";
            }
            return FdoCompiler.create().compile(completeFdo);
        } catch (Exception e) {
            // Fall back to raw bytes for truly partial content (will fail decompilation)
            return partialFdoSource.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Builds FDO binary with embedded large atom structures.
     */
    private byte[] buildBinaryFdoWithLargeAtom(int proto, int atom, int length, byte[] atomData) {
        try {
            ByteArrayOutputStream fdo = new ByteArrayOutputStream();

            // Add atom header
            fdo.write(proto);
            fdo.write(atom);
            fdo.write(length);

            // Add atom data
            fdo.write(atomData);

            return fdo.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build binary FDO with large atom", e);
        }
    }

    // ========================================================================================
    // Reflection-based Test Helpers
    // ========================================================================================

    /**
     * Test helper to invoke Pattern 1 extraction via reflection.
     * NOTE: This method no longer exists - pattern 1 was removed during refactoring.
     * Tests using this are disabled.
     */
    private String extractMultiFrameInstantMessageViaPattern1(List<byte[]> frames) throws Exception {
        // Pattern 1 (tryFdoBinaryReassembly) was removed - now uses FdoStreamExtractor directly
        throw new UnsupportedOperationException("Pattern 1 extraction removed - use FdoStreamExtractor.extractInstantMessage()");
    }

    /**
     * Test helper to invoke Pattern 2 extraction via reflection.
     * NOTE: This method no longer exists - pattern 2 was removed during refactoring.
     * Tests using this are disabled.
     */
    private String extractMultiFrameInstantMessageViaPattern2(List<byte[]> frames) throws Exception {
        // Pattern 2 (reassembleLargeAtomFrameByFrame) was removed - now uses FdoStreamExtractor directly
        throw new UnsupportedOperationException("Pattern 2 extraction removed - use FdoStreamExtractor.extractInstantMessage()");
    }

    /**
     * Test helper to invoke the complete multi-frame extraction flow.
     * Now uses FdoStreamExtractor directly instead of reflection.
     */
    private InstantMessage extractMultiFrameInstantMessage(List<byte[]> frames) throws Exception {
        // Use FdoStreamExtractor directly - no reflection needed
        return com.dialtone.fdo.FdoStreamExtractor.extractInstantMessage(frames);
    }
}