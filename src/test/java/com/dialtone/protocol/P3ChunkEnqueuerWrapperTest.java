/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.spi.impl.NativeFdoCompilationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies P3ChunkEnqueuer wraps chunks correctly.
 *
 * This test takes chunks from the Native backend, wraps them using
 * P3ChunkEnqueuer, and verifies the resulting P3 frames.
 */
@DisplayName("P3ChunkEnqueuer Wrapper Tests")
class P3ChunkEnqueuerWrapperTest {

    private static NativeFdoCompilationService nativeService;

    private static final String MINIMAL_FDO = """
        uni_start_stream <00x>
        uni_end_stream
        """;

    @BeforeAll
    static void setup() {
        nativeService = new NativeFdoCompilationService();
    }

    @Test
    @DisplayName("Test wrapP3Payload with Native chunk")
    void testWrapP3Payload_NativeChunk() throws Exception {
        int streamId = 0x2100;
        String token = "AT";

        List<FdoChunk> nativeChunks = nativeService.compileToChunks(MINIMAL_FDO, token, streamId);
        assertFalse(nativeChunks.isEmpty(), "Should produce at least one chunk");

        FdoChunk chunk = nativeChunks.get(0);
        assertNotNull(chunk.getHex(), "Chunk should have hex data");
        assertTrue(chunk.getSize() > 0, "Chunk size should be positive");

        byte[] payload = chunk.getBinaryData();
        assertNotNull(payload, "Chunk should produce binary data");

        // Wrap using wrapP3Payload (basic, no streamId in header)
        byte[] frame = P3ChunkEnqueuer.wrapP3Payload(payload);

        // Verify frame structure
        assertTrue(frame.length >= 10, "Frame should be at least 10 bytes");
        assertEquals(0x5A, frame[0] & 0xFF, "First byte should be magic 0x5A");
    }

    @Test
    @DisplayName("Test wrapP3PayloadWithStreamId with Native chunk")
    void testWrapP3PayloadWithStreamId_NativeChunk() throws Exception {
        int streamId = 0x2100;
        String token = "AT";

        List<FdoChunk> nativeChunks = nativeService.compileToChunks(MINIMAL_FDO, token, streamId);
        assertFalse(nativeChunks.isEmpty(), "Should produce at least one chunk");

        FdoChunk chunk = nativeChunks.get(0);
        byte[] payload = chunk.getBinaryData();

        // Wrap using wrapP3PayloadWithStreamId (extended, with streamId in header)
        byte[] frame = P3ChunkEnqueuer.wrapP3PayloadWithStreamId(payload, streamId);

        // Verify frame structure
        assertTrue(frame.length >= 12, "Extended frame should be at least 12 bytes");
        assertEquals(0x5A, frame[0] & 0xFF, "First byte should be magic 0x5A");

        // Extract and verify stream ID from frame header (bytes 10-11, big-endian)
        int frameStreamId = ((frame[10] & 0xFF) << 8) | (frame[11] & 0xFF);
        assertEquals(streamId, frameStreamId, "Stream ID in frame header should match");
    }

    @Test
    @DisplayName("Test enqueueChunksWithMixedStreamIds routing logic")
    void testEnqueueRouting() throws Exception {
        int streamId = 0x2100;
        String token = "AT";

        List<FdoChunk> nativeChunks = nativeService.compileToChunks(MINIMAL_FDO, token, streamId);
        assertFalse(nativeChunks.isEmpty(), "Should produce at least one chunk");

        FdoChunk chunk = nativeChunks.get(0);

        // Chunk with non-zero streamId should have streamId metadata
        assertTrue(chunk.hasStreamId(), "Chunk should have stream ID when compiled with non-zero streamId");
        assertEquals(streamId, chunk.getStreamId(), "Chunk stream ID should match");

        // Now test with streamId=0
        List<FdoChunk> chunksZero = nativeService.compileToChunks(MINIMAL_FDO, token, 0);
        assertFalse(chunksZero.isEmpty(), "Should produce at least one chunk");

        FdoChunk chunkZero = chunksZero.get(0);
        assertFalse(chunkZero.hasStreamId(), "Chunk should not have stream ID when compiled with streamId=0");
    }

    @Test
    @DisplayName("Test frame terminator")
    void testFrameTerminator() throws Exception {
        int streamId = 0x2100;
        String token = "AT";

        List<FdoChunk> nativeChunks = nativeService.compileToChunks(MINIMAL_FDO, token, streamId);
        FdoChunk chunk = nativeChunks.get(0);
        byte[] payload = chunk.getBinaryData();

        byte[] frame = P3ChunkEnqueuer.wrapP3PayloadWithStreamId(payload, streamId);

        // Last byte should be terminator (0x0D)
        assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Last byte should be terminator 0x0D");
    }
}
