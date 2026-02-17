/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Centralized processor for compiling and sending FDO templates.
 * Eliminates code duplication and enforces consistent patterns.
 */
public class FdoProcessor {

    private final FdoCompiler compiler;
    private final Pacer pacer;
    private final int maxBurstFrames;

    public FdoProcessor(FdoCompiler compiler, Pacer pacer, int maxBurstFrames) {
        this.compiler = compiler;
        this.pacer = pacer;
        this.maxBurstFrames = maxBurstFrames;
    }

    /**
     * Get the underlying FdoCompiler for direct compilation operations.
     *
     * @return the FdoCompiler instance
     */
    public FdoCompiler getCompiler() {
        return compiler;
    }

    /**
     * Compile and send FDO from a type-safe builder with session context.
     *
     * <p>This is the preferred method for new code - eliminates string-based lookup
     * and provides compile-time type safety.</p>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // Static builder (singleton)
     * fdoProcessor.compileAndSend(ctx, NoopFdo.INSTANCE, session, "At", -1, "LABEL");
     *
     * // Dynamic builder with typed config
     * fdoProcessor.compileAndSend(ctx, ConfigureActiveUsernameFdo.forUser(username), session, "At", -1, "LABEL");
     * </pre>
     *
     * @param ctx Netty channel context
     * @param builder FDO builder instance (static singleton or dynamic with config)
     * @param session Session context (provides platform, username for logging)
     * @param token P3 token
     * @param streamId P3 stream ID (or {@link FdoCompiler#AUTO_GENERATE_STREAM_ID})
     * @param label Debug label for logging
     * @throws Exception if compilation or sending fails
     */
    public void compileAndSend(
            ChannelHandlerContext ctx,
            FdoBuilder builder,
            SessionContext session,
            String token,
            int streamId,
            String label) throws Exception {

        RenderingContext renderingCtx = new RenderingContext(
                session.getPlatform(),
                false  // TODO: get from session preferences
        );

        compileAndSend(ctx, builder, renderingCtx, token, streamId, label, session.getDisplayName());
    }

    /**
     * Compile and send FDO from a type-safe builder with explicit rendering context.
     *
     * @param ctx Netty channel context
     * @param builder FDO builder instance
     * @param renderingCtx Platform and display mode context
     * @param token P3 token
     * @param streamId P3 stream ID (or {@link FdoCompiler#AUTO_GENERATE_STREAM_ID})
     * @param label Debug label for logging
     * @param username Username for logging context
     * @throws Exception if compilation or sending fails
     */
    public void compileAndSend(
            ChannelHandlerContext ctx,
            FdoBuilder builder,
            RenderingContext renderingCtx,
            String token,
            int streamId,
            String label,
            String username) throws Exception {

        // Resolve stream ID
        int actualStreamId = FdoCompiler.resolveStreamId(streamId);
        if (streamId == FdoCompiler.AUTO_GENERATE_STREAM_ID) {
            LoggerUtil.debug(() -> String.format(
                    "[%s] Auto-generated stream ID: 0x%04X", label, actualStreamId));
        }

        // Generate FDO source from builder
        String fdoSource = builder.toSource(renderingCtx);

        // Compile to P3 chunks
        List<FdoChunk> chunks = compiler.compileFdoScriptToP3Chunks(fdoSource, token, actualStreamId);

        String builderName = builder.getClass().getSimpleName();
        LoggerUtil.info(String.format(
                "[%s] Compiled %d chunks from builder %s",
                label, chunks != null ? chunks.size() : 0, builderName));

        // Enqueue chunks
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, label, maxBurstFrames, username);
    }
}
