/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.FileBrowserFdoBuilder;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Keyword handler for browsing server file storage.
 *
 * <p><b>Keyword:</b> "files" (case-insensitive)
 *
 * <p><b>Usage:</b> Type "files" in the keyword field to open the file browser.
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Opens a modal dialog showing files in the GLOBAL storage scope</li>
 *   <li>Supports navigating into subdirectories</li>
 *   <li>Clicking a file triggers the download system</li>
 *   <li>Pagination for directories with many files</li>
 * </ul>
 *
 * <p>Navigation and file downloads are handled by the FB token handler
 * in {@code ProtocolFrameDispatcher}.
 */
public class FileBrowserKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "files";
    private static final String DESCRIPTION = "Browse and download files from the server";
    private static final int MAX_BURST_FRAMES = 4;

    private final FileBrowserService fileBrowserService;
    private final FdoCompiler fdoCompiler;

    /**
     * Creates a new FileBrowserKeywordHandler.
     *
     * @param fileBrowserService the file browser service
     * @param fdoCompiler        the FDO compiler
     */
    public FileBrowserKeywordHandler(FileBrowserService fileBrowserService, FdoCompiler fdoCompiler) {
        if (fileBrowserService == null) {
            throw new IllegalArgumentException("fileBrowserService cannot be null");
        }
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("fdoCompiler cannot be null");
        }
        this.fileBrowserService = fileBrowserService;
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
            "[%s] Opening file browser (keyword: '%s')",
            username, keyword));

        // Browse root directory
        BrowseResult result = fileBrowserService.browse("/", 1);

        LoggerUtil.info(String.format(
            "[%s] File browser: path=%s, items=%d, pages=%d",
            username, result.currentPath(), result.totalItems(), result.totalPages()));

        // Build and send FDO
        sendFileBrowserFdo(ctx, pacer, result, username);
    }

    /**
     * Build and send the file browser FDO modal.
     *
     * @param ctx      channel context
     * @param pacer    frame pacer
     * @param result   browse result
     * @param username user's display name (for logging)
     */
    void sendFileBrowserFdo(ChannelHandlerContext ctx, Pacer pacer,
                            BrowseResult result, String username) throws Exception {
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, fileBrowserService);
        String fdoSource = builder.toSource(RenderingContext.DEFAULT);

        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        if (chunks != null && !chunks.isEmpty()) {
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "FILE_BROWSER", MAX_BURST_FRAMES, username);
            LoggerUtil.info(String.format(
                "[%s] Sent file browser FDO: %d chunks",
                username, chunks.size()));
        } else {
            LoggerUtil.error(String.format(
                "[%s] Failed to compile file browser FDO - no chunks generated",
                username));
        }
    }

    /**
     * Get the file browser service (for use by FB token handler).
     */
    public FileBrowserService getFileBrowserService() {
        return fileBrowserService;
    }

    /**
     * Get the FDO compiler (for use by FB token handler).
     */
    public FdoCompiler getFdoCompiler() {
        return fdoCompiler;
    }
}
