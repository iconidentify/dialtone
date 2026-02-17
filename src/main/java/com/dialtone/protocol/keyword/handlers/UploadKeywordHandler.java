/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DownloadErrorFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.xfer.XferUploadRegistry;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.protocol.xfer.XferUploadState;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Keyword handler for requesting file uploads from the client.
 *
 * <p><b>Keyword:</b> "upload" (case-insensitive)
 *
 * <p><b>Usage:</b> "upload"
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Sends th token to prompt the client's file picker</li>
 *   <li>Client selects a file and sends TH_OUT with filename</li>
 *   <li>Server sends td to request file stats</li>
 *   <li>Client sends TD_OUT with file size</li>
 *   <li>Server sends tf (0x80) to start upload</li>
 *   <li>Client streams file data (xd/xb/xe)</li>
 *   <li>Server sends fX result and stores file in uploads/{screenname}/</li>
 * </ul>
 *
 * <p>This handler triggers the server-initiated upload flow as defined in
 * xfer_developer_guide.md Appendix A.7.
 */
public class UploadKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "upload";
    private static final String DESCRIPTION = "Request file upload from client (opens file picker)";
    private static final int MAX_BURST_FRAMES = 4;

    private final XferUploadService xferUploadService;
    private final FdoCompiler fdoCompiler;

    /**
     * Creates a new UploadKeywordHandler.
     *
     * @param xferUploadService the XFER upload service for file uploads
     * @param fdoCompiler the FDO compiler for error messages
     */
    public UploadKeywordHandler(XferUploadService xferUploadService, FdoCompiler fdoCompiler) {
        if (xferUploadService == null) {
            throw new IllegalArgumentException("XferUploadService cannot be null");
        }
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        this.xferUploadService = xferUploadService;
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
        String username = session != null ? session.getDisplayName() : "unknown";

        LoggerUtil.info(String.format(
            "[%s] Received upload keyword: '%s'",
            username, keyword));

        // 1. Get the XferUploadRegistry from channel attributes
        XferUploadRegistry registry = ctx.channel().attr(StatefulClientHandler.XFER_UPLOAD_REGISTRY_KEY).get();
        if (registry == null) {
            LoggerUtil.error(String.format(
                "[%s] Cannot initiate file upload - XferUploadRegistry not found on channel",
                username));
            sendErrorFdo(ctx, pacer, username, "Upload service unavailable");
            return;
        }

        // 2. If there's an existing upload, abandon it (client closed that window)
        if (registry.abandonStaleUpload()) {
            LoggerUtil.info(String.format(
                "[%s] Previous upload abandoned - starting new upload",
                username));
        }

        // 3. Initiate upload (sends th token to prompt file picker)
        try {
            XferUploadState state = xferUploadService.initiateUpload(ctx, pacer, session, registry);

            // Send uni_wait_off to allow client to cancel without spinning
            String noopSource = NoopFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> noopChunks = fdoCompiler.compileFdoScriptToP3Chunks(
                noopSource,
                "AT",
                FdoCompiler.AUTO_GENERATE_STREAM_ID
            );
            if (noopChunks != null && !noopChunks.isEmpty()) {
                P3ChunkEnqueuer.enqueue(ctx, pacer, noopChunks, "UPLOAD_WAIT_OFF", MAX_BURST_FRAMES, username);
            }

            LoggerUtil.info(String.format(
                "[%s] Upload initiated (awaiting TH_OUT): %s",
                username, state.getUploadId()));

        } catch (IllegalStateException e) {
            LoggerUtil.error(String.format(
                "[%s] Failed to initiate upload: %s",
                username, e.getMessage()));
            sendErrorFdo(ctx, pacer, username, "Failed to start upload: " + e.getMessage());
        }
    }

    /**
     * Send error FDO to display error message to user.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param username user's display name (for logging)
     * @param errorMessage error message to display
     */
    private void sendErrorFdo(ChannelHandlerContext ctx, Pacer pacer,
                              String username, String errorMessage) {
        try {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder(errorMessage);
            String fdoSource = builder.toSource(RenderingContext.DEFAULT);

            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                fdoSource,
                "AT",
                FdoCompiler.AUTO_GENERATE_STREAM_ID
            );

            if (chunks != null && !chunks.isEmpty()) {
                P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "UPLOAD_ERROR", MAX_BURST_FRAMES, username);
                LoggerUtil.info(String.format(
                    "[%s] Sent upload error FDO: %s",
                    username, errorMessage));
            }
        } catch (Exception e) {
            LoggerUtil.error(String.format(
                "[%s] Failed to send error FDO: %s",
                username, e.getMessage()));
        }
    }
}
