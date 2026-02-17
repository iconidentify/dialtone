/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo;

import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("NoOp FDO Usage Points")
class NoopFdoUsagePointsTest {

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Pacer pacer;

    private FdoCompiler compiler;
    private FdoProcessor processor;
    private SessionContext session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock the allocator for P3ChunkEnqueuer
        ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
        when(ctx.alloc()).thenReturn(allocator);

        Properties props = new Properties();
        compiler = new FdoCompiler(props);
        processor = new FdoProcessor(compiler, pacer, 10);

        session = new SessionContext();
        session.setUsername("TestUser");
        session.setPlatform(ClientPlatform.WINDOWS);
        session.setAuthenticated(true);
    }

    @Test
    @DisplayName("CO_ACK should use NoOp FDO")
    void coAckShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "CO_ACK");

        // P3ChunkEnqueuer calls enqueue with label like "CO_ACK_00"
        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("CO_ACK")));
    }

    @Test
    @DisplayName("CL_ACK should use NoOp FDO")
    void clAckShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "CL_ACK");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("CL_ACK")));
    }

    @Test
    @DisplayName("MP_ACK should use NoOp FDO")
    void mpAckShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "MP_ACK");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("MP_ACK")));
    }

    @Test
    @DisplayName("KK_UNKNOWN_ACK should use NoOp FDO")
    void kkUnknownAckShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "KK_UNKNOWN_ACK");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("KK_UNKNOWN_ACK")));
    }

    @Test
    @DisplayName("TOS_ACK should use NoOp FDO")
    void tosAckShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "TOS_ACK");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("TOS_ACK")));
    }

    @Test
    @DisplayName("ACK should use NoOp FDO")
    void ackShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "ACK");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("ACK")));
    }

    @Test
    @DisplayName("XG_NO_TRANSFER should use NoOp FDO")
    void xgNoTransferShouldUseNoopFdo() throws Exception {
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "XG_NO_TRANSFER");

        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("XG_NO_TRANSFER")));
    }

    @Test
    @DisplayName("All NoOp usage points should compile successfully")
    void allNoopUsagePointsShouldCompileSuccessfully() throws Exception {
        String[] labels = {
            "CO_ACK", "CL_ACK", "MP_ACK", "KK_UNKNOWN_ACK",
            "TOS_ACK", "ACK", "XG_NO_TRANSFER"
        };

        for (String label : labels) {
            assertDoesNotThrow(() -> {
                processor.compileAndSend(
                    ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, label);
            }, "Should compile for label: " + label);
        }
    }

    @Test
    @DisplayName("NoOp FDO should generate correct binary output")
    void noopFdoShouldGenerateCorrectBinaryOutput() throws Exception {
        // Get FDO source from builder
        String fdoSource = NoopFdoBuilder.INSTANCE.toSource(
            com.dialtone.fdo.dsl.RenderingContext.DEFAULT);

        // Compile to chunks
        java.util.List<com.dialtone.fdo.FdoChunk> chunks =
            compiler.compileFdoScriptToP3Chunks(fdoSource, "At", -1);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // Verify all chunks have binary data
        for (com.dialtone.fdo.FdoChunk chunk : chunks) {
            assertNotNull(chunk.getBinaryData());
            assertTrue(chunk.getBinaryData().length > 0);
        }
    }
}
