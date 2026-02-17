/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoTemplateEngine;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.GidUtils;
import com.dialtone.protocol.dod.DodRequestHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for f1 token handling (atom stream requests).
 * Tests GID parsing, FDO file lookup, binary-to-hex conversion, and response generation.
 */
@DisplayName("F1 Token Handler Tests")
public class F1TokenHandlerTest {

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
    @DisplayName("GID Parsing Tests")
    class GidParsingTests {

        @Test
        @DisplayName("Should parse 32-bit GID to display format with 3 parts")
        void testGidParsingThreeParts() {
            // GID 0x28B9B800 = 40-185-47104 but with different byte order
            // Based on the example: 40-47544 => byte3=0, byte2=40, word=47544
            // 0x0028B9B8 = 0 - 40 - 47544

            // GID where byte3 = 0, byte2 = 40, word = 47544
            // 0x0028B9B8 = (0 << 24) | (40 << 16) | 47544
            int gid = (0 << 24) | (40 << 16) | 47544;
            String result = GidUtils.formatToDisplay(gid);

            assertEquals("40-47544", result);
        }

        @Test
        @DisplayName("Should parse GID with non-zero byte3 to 3-part format")
        void testGidParsingWithByte3() {
            // GID 0x01000535 = 1-0-1333
            int gid = (1 << 24) | (0 << 16) | 1333;
            String result = GidUtils.formatToDisplay(gid);

            assertEquals("1-0-1333", result);
        }

        @Test
        @DisplayName("Should parse GID 32-117 correctly")
        void testGidParsing32_117() {
            // GID where byte3 = 0, byte2 = 32, word = 117
            // 0x00200075 = (0 << 24) | (32 << 16) | 117
            int gid = (0 << 24) | (32 << 16) | 117;
            String result = GidUtils.formatToDisplay(gid);

            assertEquals("32-117", result);
        }
    }

    @Nested
    @DisplayName("Atom Stream Response Tests")
    class AtomStreamResponseTests {

        @Test
        @DisplayName("Should return empty response when FDO file not found")
        void testFdoFileNotFound() throws Exception {
            // Request a GID that doesn't have a corresponding FDO file
            // Note: word part must fit in 16 bits (max 65535)
            int gid = (0 << 24) | (99 << 16) | 12345; // 99-12345 - doesn't exist

            DodRequestHandler.AtomStreamResponse response =
                dodRequestHandler.processAtomStreamRequest(mockCtx, gid, 0x1234, "testuser", ClientPlatform.WINDOWS);

            assertNotNull(response);
            assertFalse(response.found);
            assertTrue(response.responseChunks.isEmpty());
            assertEquals("99-12345", response.gid);
        }

        @Test
        @DisplayName("Should find and process FDO file for GID 40-47544")
        void testFdoFileFound() throws Exception {
            // Request GID 40-47544 which has a corresponding FDO file
            // 40-47544 = byte2=40 (0x28), word=47544 (0xB9B8)
            int gid = (0 << 24) | (40 << 16) | 47544;

            DodRequestHandler.AtomStreamResponse response =
                dodRequestHandler.processAtomStreamRequest(mockCtx, gid, 0x1234, "testuser", ClientPlatform.WINDOWS);

            assertNotNull(response);
            assertEquals("40-47544", response.gid);
            // In mock mode, the FdoCompiler will generate mock chunks
            // The file should be found
            assertTrue(response.found || !response.responseChunks.isEmpty());
        }

        @Test
        @DisplayName("Should preserve stream ID in response")
        void testStreamIdPreservation() throws Exception {
            // Use a GID that doesn't exist to avoid needing the FDO file
            int gid = (0 << 24) | (88 << 16) | 8888;
            int expectedStreamId = 0xABCD;

            DodRequestHandler.AtomStreamResponse response =
                dodRequestHandler.processAtomStreamRequest(mockCtx, gid, expectedStreamId, "testuser", ClientPlatform.WINDOWS);

            assertNotNull(response);
            assertEquals(expectedStreamId, response.streamId);
        }
    }

    @Nested
    @DisplayName("Byte Array to FDO Hex Conversion Tests (via FdoTemplateEngine)")
    class ByteArrayToHexTests {

        @Test
        @DisplayName("Should convert byte array to FDO hex format in template")
        void testByteArrayToHexConversion() {
            Map<String, Object> variables = new HashMap<>();
            byte[] testData = new byte[] { 0x01, 0x02, 0x03, (byte)0xFF };
            variables.put("TEST_DATA", testData);

            String template = "data <{{TEST_DATA}}>";
            String result = FdoTemplateEngine.substituteVariables(template, variables);

            assertNotNull(result);
            // byte[] values are auto-converted to FDO hex format: 01x,02x,03x,ffx
            assertEquals("data <01x,02x,03x,ffx>", result.toLowerCase());
        }

        @Test
        @DisplayName("Should handle empty byte array")
        void testEmptyByteArray() {
            Map<String, Object> variables = new HashMap<>();
            byte[] emptyData = new byte[0];
            variables.put("TEST_DATA", emptyData);

            String template = "data <{{TEST_DATA}}>";
            String result = FdoTemplateEngine.substituteVariables(template, variables);

            assertNotNull(result);
            // Empty byte[] should produce "00x" placeholder
            assertEquals("data <00x>", result.toLowerCase());
        }

        @Test
        @DisplayName("Should handle large byte array as single output")
        void testLargeByteArrayNoSplitting() {
            Map<String, Object> variables = new HashMap<>();
            byte[] largeData = new byte[300];
            for (int i = 0; i < 300; i++) {
                largeData[i] = (byte) (i % 256);
            }
            variables.put("TEST_DATA", largeData);

            String template = "data <{{TEST_DATA}}>";
            String result = FdoTemplateEngine.substituteVariables(template, variables);

            assertNotNull(result);
            // Should be a single output with all hex values (no line splitting)
            assertFalse(result.contains("\n"), "Should not contain newlines - native compiler handles chunking");
            // Should contain all 300 hex pairs
            int hexPairCount = result.split(",").length;
            assertEquals(300, hexPairCount, "Should have 300 hex pairs");
        }
    }

    @Nested
    @DisplayName("F1 Frame Parsing Tests")
    class F1FrameParsingTests {

        @Test
        @DisplayName("Should recognize f1 token bytes (0x66 0x31)")
        void testF1TokenBytes() {
            // f1 = 'f' '1' = 0x66 0x31
            byte f = 0x66;
            byte one = 0x31;

            assertEquals('f', (char) f);
            assertEquals('1', (char) one);
        }

        @Test
        @DisplayName("Should correctly extract GID from f1 frame structure")
        void testF1GidExtraction() {
            // f1 frame structure after f1 token (at offset 8-9):
            //   Offset +0-1:  f1 token (66 31)
            //   Offset +2-3:  Stream ID
            //   Offset +4-7:  Flags/padding (00 01 00 00)
            //   Offset +8-9:  Length/type marker (0e 04)
            //   Offset +10-13: GID (4 bytes, big-endian)

            // For GID 40-47544 = 0x0028B9B8
            // Bytes would be: 00 28 B9 B8 at offset +10
            byte[] gidBytes = new byte[] { 0x00, 0x28, (byte)0xB9, (byte)0xB8 };

            int extracted = ((gidBytes[0] & 0xFF) << 24) |
                           ((gidBytes[1] & 0xFF) << 16) |
                           ((gidBytes[2] & 0xFF) << 8) |
                           (gidBytes[3] & 0xFF);

            // Verify this produces the expected GID value
            int byte3 = (extracted >> 24) & 0xFF;  // 0
            int byte2 = (extracted >> 16) & 0xFF;  // 40
            int word = extracted & 0xFFFF;          // 47544

            assertEquals(0, byte3);
            assertEquals(40, byte2);
            assertEquals(47544, word);
        }
    }

    @Nested
    @DisplayName("AtomStreamResponse Tests")
    class AtomStreamResponseClassTests {

        @Test
        @DisplayName("Should create valid AtomStreamResponse with all fields")
        void testAtomStreamResponseCreation() {
            List<FdoChunk> chunks = List.of(new FdoChunk(0, 10, "0102030405060708090A"));

            DodRequestHandler.AtomStreamResponse response =
                new DodRequestHandler.AtomStreamResponse(0x1234, "40-47544", chunks, true);

            assertEquals(0x1234, response.streamId);
            assertEquals("40-47544", response.gid);
            assertEquals(1, response.responseChunks.size());
            assertTrue(response.found);
        }

        @Test
        @DisplayName("Should handle not-found response correctly")
        void testNotFoundResponse() {
            DodRequestHandler.AtomStreamResponse response =
                new DodRequestHandler.AtomStreamResponse(0x5678, "99-12345", List.of(), false);

            assertEquals(0x5678, response.streamId);
            assertEquals("99-12345", response.gid);
            assertTrue(response.responseChunks.isEmpty());
            assertFalse(response.found);
        }
    }
}
