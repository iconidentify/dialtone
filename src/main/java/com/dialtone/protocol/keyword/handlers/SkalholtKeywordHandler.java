/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.skalholt.fdo.SkalholtFdoBuilder;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

import static com.dialtone.fdo.FdoCompiler.AUTO_GENERATE_STREAM_ID;
import static com.dialtone.fdo.FdoCompiler.resolveStreamId;

public class SkalholtKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "skalholt";
    private static final String DESCRIPTION = "Skalholt Game";

    private final FdoCompiler fdoCompiler;
    private int MAX_BURST_FRAMES = 10;

    public SkalholtKeywordHandler(FdoCompiler fdoCompiler) {
        this.fdoCompiler = fdoCompiler;
    }

    @Override
    public String getKeyword() {
        return KEYWORD;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }



    @Override
    public void handle(String keyword, SessionContext session,
                       ChannelHandlerContext ctx, Pacer pacer) throws Exception {

        SkalholtFdoBuilder builder = new SkalholtFdoBuilder();
        String fdoSource = builder.toSource();
        LoggerUtil.info("Sending Skalholt FDO to user: " + session.getDisplayName());
        LoggerUtil.info(fdoSource);
        int streamId = resolveStreamId(AUTO_GENERATE_STREAM_ID);
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "AT", streamId);
        String noopSource = NoopFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
        chunks.addAll(fdoCompiler.compileFdoScriptToP3Chunks(noopSource, "At", streamId));
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "SKALHOLT", MAX_BURST_FRAMES, session.getDisplayName());

        // Initialize telnet bridge after sending initial Skalholt FDO
        StatefulClientHandler handler = ctx.channel().attr(StatefulClientHandler.HANDLER_KEY).get();
        if (handler != null) {
            handler.initializeSkalholtTelnetBridge(ctx);
        } else {
            LoggerUtil.warn("[SkalholtKeywordHandler] Cannot initialize telnet bridge - StatefulClientHandler not found on channel");
        }
    }
}
