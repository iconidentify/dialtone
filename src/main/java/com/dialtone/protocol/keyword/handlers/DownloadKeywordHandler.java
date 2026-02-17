/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DownloadErrorFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.storage.FileStorage;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.List;

/**
 * Keyword handler for downloading files from the server's storage.
 *
 * <p><b>Keyword:</b> "download" (case-insensitive, parameterized)
 *
 * <p><b>Usage:</b> "download &lt;filename&gt;"
 * <br>Example: "download fetch-403.hqx"
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Loads file from storage (filesystem first, then classpath fallback)</li>
 *   <li>Sanitizes filename to prevent directory traversal attacks</li>
 *   <li>Initiates XFER protocol transfer with proper xG handshake</li>
 *   <li>Sends error FDO if file not found or invalid filename</li>
 * </ul>
 *
 * <p>Files are loaded from storage/global/ directory (configurable via
 * storage.local.base.dir property). Falls back to classpath resources
 * (downloads/) for bundled files when not found on filesystem.
 */
public class DownloadKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "download";
    private static final String DESCRIPTION = "Download a file from the server (usage: download <filename>)";
    private static final int MAX_BURST_FRAMES = 4;

    private final XferService xferService;
    private final FdoCompiler fdoCompiler;
    private final FileStorage fileStorage;

    /**
     * Creates a new DownloadKeywordHandler.
     *
     * @param xferService the XFER service for file transfer
     * @param fdoCompiler the FDO compiler for error messages
     * @param fileStorage the file storage for reading downloadable files
     */
    public DownloadKeywordHandler(XferService xferService, FdoCompiler fdoCompiler, FileStorage fileStorage) {
        if (xferService == null) {
            throw new IllegalArgumentException("XferService cannot be null");
        }
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        if (fileStorage == null) {
            throw new IllegalArgumentException("FileStorage cannot be null");
        }
        this.xferService = xferService;
        this.fdoCompiler = fdoCompiler;
        this.fileStorage = fileStorage;
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
            "[%s] Received download keyword: '%s'",
            username, keyword));

        // 1. Extract filename from keyword (e.g., "download fetch-403.hqx" -> "fetch-403.hqx")
        String rawFilename = extractFilename(keyword);
        if (rawFilename == null || rawFilename.isEmpty()) {
            sendErrorFdo(ctx, pacer, username, "Usage: download <filename>");
            return;
        }

        // 2. Sanitize filename to prevent directory traversal
        String filename = sanitizeFilename(rawFilename);
        if (filename == null || filename.isEmpty()) {
            LoggerUtil.warn(String.format(
                "[%s] Invalid filename rejected: '%s'",
                username, rawFilename));
            sendErrorFdo(ctx, pacer, username, "Invalid filename: " + rawFilename);
            return;
        }

        // 3. Load file from storage (tries filesystem first, then classpath)
        byte[] fileData;
        try {
            fileData = fileStorage.readAllBytes(FileStorage.Scope.GLOBAL, null, filename);
        } catch (IOException e) {
            LoggerUtil.warn(String.format(
                "[%s] File not found: '%s'",
                username, filename));
            sendErrorFdo(ctx, pacer, username, "File not found: " + filename);
            return;
        }

        LoggerUtil.info(String.format(
            "[%s] Loading file for download: %s (%d bytes)",
            username, filename, fileData.length));

        // 4. Get the XferTransferRegistry from channel attributes
        XferTransferRegistry registry = ctx.channel().attr(StatefulClientHandler.XFER_REGISTRY_KEY).get();
        if (registry == null) {
            LoggerUtil.error(String.format(
                "[%s] Cannot initiate file transfer - XferTransferRegistry not found on channel",
                username));
            sendErrorFdo(ctx, pacer, username, "Transfer service unavailable");
            return;
        }

        // 5. Initiate transfer (sends atoms + tj + tf, then waits for xG)
        xferService.initiateTransfer(ctx, pacer, filename, fileData, session, registry);

        LoggerUtil.info(String.format(
            "[%s] Download initiated (awaiting xG): %s (%d bytes)",
            username, filename, fileData.length));
    }

    /**
     * Extract filename from the keyword string.
     *
     * @param keyword full keyword (e.g., "download fetch-403.hqx")
     * @return filename or null if not present
     */
    private String extractFilename(String keyword) {
        if (keyword == null) {
            return null;
        }

        // Handle case where keyword is just "download" or "download "
        String trimmed = keyword.trim();
        if (trimmed.equalsIgnoreCase(KEYWORD)) {
            return null;
        }

        // Find first space after "download"
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0 || spaceIndex >= trimmed.length() - 1) {
            return null;
        }

        return trimmed.substring(spaceIndex + 1).trim();
    }

    /**
     * Sanitize filename to prevent directory traversal attacks.
     *
     * <p>Only allows alphanumeric characters, dots, underscores, and hyphens.
     * Removes path separators and parent directory references.
     *
     * @param filename raw filename from user
     * @return sanitized filename or null if invalid
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        // Remove path separators and parent directory references
        String sanitized = filename
            .replaceAll("[/\\\\]", "")   // Remove / and \
            .replaceAll("\\.\\.", "");    // Remove ..

        // Validate: only allow alphanumeric, dots, underscores, hyphens
        if (!sanitized.matches("^[a-zA-Z0-9._-]+$")) {
            return null;
        }

        // Must have at least one character before any extension
        if (sanitized.startsWith(".")) {
            return null;
        }

        return sanitized;
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
                P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "DOWNLOAD_ERROR", MAX_BURST_FRAMES, username);
                LoggerUtil.info(String.format(
                    "[%s] Sent download error FDO: %s",
                    username, errorMessage));
            }
        } catch (Exception e) {
            LoggerUtil.error(String.format(
                "[%s] Failed to send error FDO: %s",
                username, e.getMessage()));
        }
    }
}
