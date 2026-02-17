/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.filebrowser;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DownloadErrorFdoBuilder;
import com.dialtone.fdo.dsl.builders.FileBrowserFdoBuilder;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.protocol.MultiFrameStreamProcessor;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.storage.FileStorage;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token handler for file browser navigation and downloads.
 *
 * <p>Handles the FB token which is sent when users:
 * <ul>
 *   <li>Click a folder - navigate to that directory</li>
 *   <li>Click a file - trigger download</li>
 *   <li>Click Back button - go to parent directory</li>
 * </ul>
 *
 * <p>Payload format (sent from FDO via de_get_data_pointer on list item title):
 * <ul>
 *   <li>Directory: "D:foldername" - navigate into folder</li>
 *   <li>File: "F:filename" - trigger download</li>
 *   <li>Back: "BACK" - go to parent directory</li>
 * </ul>
 */
public class FileBrowserTokenHandler implements TokenHandler {

    private static final String TOKEN = "FB";
    private static final String DIR_PREFIX = "D:";
    private static final String FILE_PREFIX = "F:";
    private static final String BACK_COMMAND = "BACK";
    private static final int MAX_BURST_FRAMES = 4;

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FileBrowserService fileBrowserService;
    private final XferService xferService;
    private final FileStorage fileStorage;
    private final String logPrefix;

    // Track current directory per session (keyed by session display name)
    private static final Map<String, String> sessionCurrentPaths = new ConcurrentHashMap<>();

    // Multi-frame stream accumulation
    private final Map<Integer, List<byte[]>> pendingFbStreams = new ConcurrentHashMap<>();

    /**
     * Creates a new FileBrowserTokenHandler.
     *
     * @param session            the session context
     * @param pacer              the frame pacer
     * @param fdoCompiler        the FDO compiler
     * @param fileBrowserService the file browser service
     * @param xferService        the XFER service for file downloads
     * @param fileStorage        the file storage
     */
    public FileBrowserTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                                    FileBrowserService fileBrowserService, XferService xferService,
                                    FileStorage fileStorage) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fileBrowserService = fileBrowserService;
        this.xferService = xferService;
        this.fileStorage = fileStorage;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    /**
     * Initialize session path to root when opening file browser.
     * Call this when first opening the file browser window.
     */
    public static void initializeSessionPath(String sessionKey) {
        sessionCurrentPaths.put(sessionKey, "/");
    }

    /**
     * Clear session path when session ends.
     * Call this on disconnect to prevent memory leaks.
     */
    public static void clearSessionPath(String sessionKey) {
        sessionCurrentPaths.remove(sessionKey);
    }

    @Override
    public boolean canHandle(String token) {
        return TOKEN.equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        int streamId = MultiFrameStreamProcessor.extractStreamId(frame);

        // Check if this is end of stream
        if (MultiFrameStreamProcessor.isUniEndStream(frame, fdoCompiler)) {
            List<byte[]> accumulatedFrames = pendingFbStreams.get(streamId);

            String payload;
            if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                List<byte[]> allFrames = new ArrayList<>(accumulatedFrames);
                allFrames.add(frame);
                payload = MultiFrameStreamProcessor.extractDeDataFromMultiFrame(allFrames, fdoCompiler, TOKEN);
                pendingFbStreams.remove(streamId);
            } else {
                payload = MultiFrameStreamProcessor.extractDeDataFromSingleFrame(frame, fdoCompiler, TOKEN);
            }

            processPayload(ctx, payload);
        } else {
            // Accumulate multi-frame stream
            pendingFbStreams.computeIfAbsent(streamId, k -> new ArrayList<>()).add(
                Arrays.copyOf(frame, frame.length)
            );
        }
    }

    /**
     * Process the decoded payload.
     * Format: "D:name" (directory), "F:name" (file), or "BACK"
     */
    private void processPayload(ChannelHandlerContext ctx, String payload) {
        if (payload == null || payload.isEmpty()) {
            LoggerUtil.warn(logPrefix + "FB token with empty payload");
            return;
        }

        LoggerUtil.info(logPrefix + "FB token payload: " + payload);

        // Get session key for path tracking
        String sessionKey = session != null ? session.getDisplayName() : "unknown";
        String currentPath = sessionCurrentPaths.getOrDefault(sessionKey, "/");

        if (BACK_COMMAND.equals(payload)) {
            // Navigate to parent directory
            String parentPath = getParentPath(currentPath);
            LoggerUtil.info(logPrefix + "FB BACK: " + currentPath + " -> " + parentPath);
            sessionCurrentPaths.put(sessionKey, parentPath);
            sendFileBrowserUpdate(ctx, parentPath);
        } else if (payload.startsWith(DIR_PREFIX)) {
            // Navigate into directory
            String dirName = payload.substring(DIR_PREFIX.length());
            String newPath = combinePath(currentPath, dirName);
            LoggerUtil.info(logPrefix + "FB navigate: " + currentPath + " + " + dirName + " -> " + newPath);
            sessionCurrentPaths.put(sessionKey, newPath);
            sendFileBrowserUpdate(ctx, newPath);
        } else if (payload.startsWith(FILE_PREFIX)) {
            // Download file
            String fileName = payload.substring(FILE_PREFIX.length());
            String fullPath = combinePath(currentPath, fileName);
            LoggerUtil.info(logPrefix + "FB download: " + fullPath);
            handleDownload(ctx, fullPath);
        } else {
            LoggerUtil.warn(logPrefix + "FB unknown payload format: " + payload);
        }
    }

    /**
     * Get parent path.
     */
    private String getParentPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return "/";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Combine directory path with name.
     */
    private String combinePath(String dirPath, String name) {
        if (dirPath == null || dirPath.isEmpty() || dirPath.equals("/")) {
            return "/" + name;
        }
        return dirPath + "/" + name;
    }

    /**
     * Send file browser update for path.
     */
    private void sendFileBrowserUpdate(ChannelHandlerContext ctx, String path) {
        BrowseResult result = fileBrowserService.browse(path, 1);
        LoggerUtil.info(logPrefix + "FB browse: path=" + result.currentPath() +
            ", items=" + result.totalItems());
        try {
            sendFileBrowserFdo(ctx, result);
        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Failed to send file browser FDO: " + e.getMessage());
        }
    }

    /**
     * Handle download request.
     * @param fullPath the full path to the file (e.g., "/subdir/file.txt")
     */
    private void handleDownload(ChannelHandlerContext ctx, String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            LoggerUtil.error(logPrefix + "Invalid download path: " + fullPath);
            sendError(ctx, "Invalid download request");
            return;
        }

        LoggerUtil.info(logPrefix + "File browser download: " + fullPath);

        // Extract just the filename from the path
        String filename = extractFilename(fullPath);
        if (filename == null || filename.isEmpty()) {
            sendError(ctx, "Invalid file path");
            return;
        }

        // Security: Sanitize and validate path is within storage
        if (!fileBrowserService.isValidPath(fullPath)) {
            LoggerUtil.warn(logPrefix + "Download path outside storage: " + fullPath);
            sendError(ctx, "File not accessible");
            return;
        }

        // Read file from storage using the path relative to storage root
        byte[] fileData;
        try {
            // The fullPath is relative to storage root (e.g., "/subdir/file.txt")
            // We need to convert it to work with FileStorage
            String relativePath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
            fileData = fileStorage.readAllBytes(FileStorage.Scope.GLOBAL, null, relativePath);
        } catch (IOException e) {
            LoggerUtil.warn(logPrefix + "File not found: " + fullPath);
            sendError(ctx, "File not found: " + filename);
            return;
        }

        LoggerUtil.info(logPrefix + "Initiating download: " + filename + " (" + fileData.length + " bytes)");

        // Get XFER registry from channel
        XferTransferRegistry registry = ctx.channel().attr(StatefulClientHandler.XFER_REGISTRY_KEY).get();
        if (registry == null) {
            LoggerUtil.error(logPrefix + "Cannot initiate file transfer - XferTransferRegistry not found");
            sendError(ctx, "Transfer service unavailable");
            return;
        }

        // Initiate transfer
        xferService.initiateTransfer(ctx, pacer, filename, fileData, session, registry);

        LoggerUtil.info(logPrefix + "Download initiated (awaiting xG): " + filename);
    }

    /**
     * Send file browser FDO modal.
     */
    private void sendFileBrowserFdo(ChannelHandlerContext ctx, BrowseResult result) throws Exception {
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, fileBrowserService);
        String fdoSource = builder.toSource(RenderingContext.DEFAULT);

        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        if (chunks != null && !chunks.isEmpty()) {
            String username = session != null ? session.getDisplayName() : "unknown";
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "FILE_BROWSER", MAX_BURST_FRAMES, username);
            LoggerUtil.info(logPrefix + "Sent file browser FDO: " + chunks.size() + " chunks");
        }
    }

    /**
     * Send error message FDO.
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder(message);
            String fdoSource = builder.toSource(RenderingContext.DEFAULT);

            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                fdoSource,
                "AT",
                FdoCompiler.AUTO_GENERATE_STREAM_ID
            );

            if (chunks != null && !chunks.isEmpty()) {
                String username = session != null ? session.getDisplayName() : "unknown";
                P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "FB_ERROR", MAX_BURST_FRAMES, username);
            }
        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Failed to send error FDO: " + e.getMessage());
        }
    }

    /**
     * Extract filename from full path.
     */
    private String extractFilename(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < fullPath.length() - 1) {
            return fullPath.substring(lastSlash + 1);
        }
        return fullPath;
    }
}
