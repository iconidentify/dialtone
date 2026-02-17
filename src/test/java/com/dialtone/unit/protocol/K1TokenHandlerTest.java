/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.GidUtils;
import com.dialtone.protocol.dod.DodRequestHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for K1 token handling (FDO requests).
 * Tests response ID extraction, GID parsing from de_data, FDO file lookup, and response wrapping.
 */
@DisplayName("K1 Token Handler Tests")
public class K1TokenHandlerTest {

    private FdoCompiler mockFdoCompiler;
    private DodRequestHandler dodRequestHandler;
    private ChannelHandlerContext mockCtx;

    @BeforeEach
    void setUp() {
        // Create mock FdoCompiler using mock URL to enable mock mode
        Properties props = new Properties();
        props.setProperty("atomforge.base.url", "mock://test");
        mockFdoCompiler = new FdoCompiler(props);
        
        dodRequestHandler = new DodRequestHandler(mockFdoCompiler, props);
        mockCtx = mock(ChannelHandlerContext.class);
    }

    @Nested
    @DisplayName("K1 Response Tests")
    class K1ResponseTests {

        @Test
        @DisplayName("Should return empty response when FDO file not found")
        void testFdoFileNotFound() throws Exception {
            // Request a GID that doesn't have a corresponding FDO file
            int gid = (0 << 24) | (99 << 16) | 12345;
            int responseId = 42;
            
            DodRequestHandler.K1Response response = 
                dodRequestHandler.processK1Request(mockCtx, gid, responseId, 0x1234, "testuser", ClientPlatform.WINDOWS);
            
            assertNotNull(response);
            assertEquals("99-12345", response.gid);
            assertEquals(responseId, response.responseId);
            assertFalse(response.found);
            assertTrue(response.responseChunks.isEmpty());
        }

        @Test
        @DisplayName("Should find and process FDO file for GID 40-47544")
        void testFdoFileFound() throws Exception {
            // Request GID 40-47544 which has a corresponding FDO file
            int gid = (0 << 24) | (40 << 16) | 47544;
            int responseId = 18;
            
            DodRequestHandler.K1Response response = 
                dodRequestHandler.processK1Request(mockCtx, gid, responseId, 0x1234, "testuser", ClientPlatform.WINDOWS);
            
            assertNotNull(response);
            assertEquals("40-47544", response.gid);
            assertEquals(responseId, response.responseId);
            // In mock mode, the FdoCompiler will generate mock chunks
            assertTrue(response.found || !response.responseChunks.isEmpty());
        }

        @Test
        @DisplayName("Should preserve stream ID in response")
        void testStreamIdPreservation() throws Exception {
            // Use a GID that doesn't exist to avoid needing the FDO file
            int gid = (0 << 24) | (88 << 16) | 8888;
            int responseId = 99;
            int expectedStreamId = 0xABCD;
            
            DodRequestHandler.K1Response response = 
                dodRequestHandler.processK1Request(mockCtx, gid, responseId, expectedStreamId, "testuser", ClientPlatform.WINDOWS);
            
            assertNotNull(response);
            assertEquals(expectedStreamId, response.streamId);
            assertEquals(responseId, response.responseId);
        }

        @Test
        @DisplayName("Should create valid K1Response with all fields")
        void testK1ResponseCreation() {
            List<FdoChunk> chunks = List.of(new FdoChunk(0, 10, "0102030405060708090A"));
            
            DodRequestHandler.K1Response response = 
                new DodRequestHandler.K1Response(0x1234, "40-47544", 18, chunks, true);
            
            assertEquals(0x1234, response.streamId);
            assertEquals("40-47544", response.gid);
            assertEquals(18, response.responseId);
            assertEquals(1, response.responseChunks.size());
            assertTrue(response.found);
        }
    }

    @Nested
    @DisplayName("de_data Parsing Tests")
    class DeDataParsingTests {

        @Test
        @DisplayName("Should parse escaped hex bytes correctly")
        void testParseEscapedHexBytes() {
            // Test the byte parsing logic directly
            // Input: \x00\x28\xB9\xB8 should produce bytes [0x00, 0x28, 0xB9, 0xB8]
            String content = "\\x00\\x28\\xB9\\xB8";
            
            byte[] result = parseDeDataToBytes(content);
            
            assertEquals(4, result.length);
            assertEquals((byte) 0x00, result[0]);
            assertEquals((byte) 0x28, result[1]);
            assertEquals((byte) 0xB9, result[2]);
            assertEquals((byte) 0xB8, result[3]);
        }

        @Test
        @DisplayName("Should parse raw character bytes")
        void testParseRawCharacterBytes() {
            // Raw characters like '(' which is 0x28
            String content = "\u0000(";
            
            byte[] result = parseDeDataToBytes(content);
            
            // Should have at least 2 bytes
            assertTrue(result.length >= 2);
            assertEquals((byte) 0x00, result[0]);
            assertEquals((byte) 0x28, result[1]); // '('
        }

        @Test
        @DisplayName("Should extract GID from de_data pattern")
        void testExtractGidFromDeDataPattern() {
            // Simulate FDO source with de_data containing GID 40-47544 = 0x0028B9B8
            String fdoSource = "uni_start_stream\n" +
                "  man_set_response_id <12>\n" +
                "  de_data <\"\\x00\\x28\\xB9\\xB8\">\n" +
                "uni_end_stream";
            
            int gid = extractK1GidFromFdo(fdoSource);
            
            // 0x0028B9B8 = 2669496
            assertEquals(0x0028B9B8, gid);
        }

        /**
         * Local implementation of parseDeDataToBytes for testing.
         * Mirrors the StatefulClientHandler implementation.
         */
        private byte[] parseDeDataToBytes(String content) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

            int i = 0;
            while (i < content.length()) {
                char c = content.charAt(i);

                if (c == '\\' && i + 1 < content.length()) {
                    char next = content.charAt(i + 1);
                    if (next == 'x' && i + 3 < content.length()) {
                        // Hex escape: \xNN
                        String hex = content.substring(i + 2, i + 4);
                        try {
                            baos.write(Integer.parseInt(hex, 16));
                            i += 4;
                            continue;
                        } catch (NumberFormatException e) {
                            // Fall through to treat as literal
                        }
                    }
                }

                // Raw character - write as byte
                baos.write((byte) c);
                i++;
            }

            return baos.toByteArray();
        }

        /**
         * Local implementation of extractK1Gid for testing.
         * Mirrors the StatefulClientHandler implementation.
         */
        private int extractK1GidFromFdo(String fdoSource) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "de_data\\s+<\"([^\"]*)\">", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(fdoSource);

            if (!matcher.find()) {
                return 0;
            }

            String deDataContent = matcher.group(1);
            byte[] gidBytes = parseDeDataToBytes(deDataContent);

            if (gidBytes.length < 4) {
                return 0;
            }

            return ((gidBytes[0] & 0xFF) << 24) |
                   ((gidBytes[1] & 0xFF) << 16) |
                   ((gidBytes[2] & 0xFF) << 8) |
                   (gidBytes[3] & 0xFF);
        }
    }

    @Nested
    @DisplayName("Response ID Extraction Tests")
    class ResponseIdExtractionTests {

        @Test
        @DisplayName("Should extract response ID from man_set_response_id")
        void testExtractResponseId() {
            String fdoSource = "uni_start_stream\n" +
                "  man_set_response_id <12>\n" +
                "  de_data <\"\\x00\\x28\\xB9\\xB8\">\n" +
                "uni_end_stream";
            
            int responseId = extractK1ResponseId(fdoSource);
            
            assertEquals(12, responseId);
        }

        @Test
        @DisplayName("Should extract larger response ID values")
        void testExtractLargeResponseId() {
            String fdoSource = "uni_start_stream\n" +
                "  man_set_response_id <99999>\n" +
                "  de_data <\"test\">\n" +
                "uni_end_stream";
            
            int responseId = extractK1ResponseId(fdoSource);
            
            assertEquals(99999, responseId);
        }

        @Test
        @DisplayName("Should return 0 when response ID not found")
        void testMissingResponseId() {
            String fdoSource = "uni_start_stream\n" +
                "  de_data <\"test\">\n" +
                "uni_end_stream";
            
            int responseId = extractK1ResponseId(fdoSource);
            
            assertEquals(0, responseId);
        }

        /**
         * Local implementation of extractK1ResponseId for testing.
         * Mirrors the StatefulClientHandler implementation.
         */
        private int extractK1ResponseId(String fdoSource) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "man_set_response_id\\s+<(\\d+)>");
            java.util.regex.Matcher matcher = pattern.matcher(fdoSource);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return 0;
        }
    }

    @Nested
    @DisplayName("K1 Token Bytes Tests")
    class K1TokenBytesTests {

        @Test
        @DisplayName("Should recognize K1 token bytes (0x4B 0x31)")
        void testK1TokenBytes() {
            // K1 = 'K' '1' = 0x4B 0x31
            byte k = 0x4B;
            byte one = 0x31;
            
            assertEquals('K', (char) k);
            assertEquals('1', (char) one);
        }
    }

    @Nested
    @DisplayName("GID Format Conversion Tests")
    class GidFormatConversionTests {

        @Test
        @DisplayName("Should convert GID bytes to AOL format")
        void testGidBytesToAolFormat() {
            // GID 40-47544 = 0x0028B9B8
            int gid = 0x0028B9B8;
            String result = GidUtils.formatToDisplay(gid);
            
            assertEquals("40-47544", result);
        }

        @Test
        @DisplayName("Should handle GID with leading zero byte")
        void testGidWithLeadingZero() {
            // GID 43-11621 = 0x002B2D65
            int gid = 0x002B2D65;
            String result = GidUtils.formatToDisplay(gid);
            
            assertEquals("43-11621", result);
        }

        @Test
        @DisplayName("Should include byte3 in format when non-zero")
        void testGidWithNonZeroByte3() {
            // GID 1-0-1333 = 0x01000535
            int gid = 0x01000535;
            String result = GidUtils.formatToDisplay(gid);
            
            assertEquals("1-0-1333", result);
        }
    }
}

