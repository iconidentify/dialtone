/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.WriteHandle;
import com.dialtone.storage.impl.LocalFileSystemStorage;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing XFER file uploads (client to server).
 *
 * <p>Orchestrates the upload protocol with proper phase transitions:
 * <ol>
 *   <li>Phase 1 (TH): Send th token to prompt file picker</li>
 *   <li>Wait for TH_OUT response with filename</li>
 *   <li>Phase 2 (TD): Send td token to request file stats</li>
 *   <li>Wait for TD_OUT response with file size</li>
 *   <li>Phase 3 (TF): Send tf token with 0x80 flag to start upload</li>
 *   <li>Receive xd/xb/xe stream until xe (end) received</li>
 *   <li>Phase 4 (FX): Send fX token with result</li>
 * </ol>
 *
 * <p><b>Protocol Flow (from xfer_developer_guide.md A.7):</b>
 * <pre>
 * Server                              Client
 *   |-------- th (TH_IN) ------------>|   Prompt file picker
 *   |<------- TH_OUT -----------------|   token[2], count=1, filename[116]
 *   |-------- td (TD_IN) ------------>|   Request file stats
 *   |<------- TD_OUT -----------------|   token[2], rc, size[3], metadata
 *   |-------- tf (0x80) ------------->|   Start upload (NO xG response!)
 *   |<------- xd (data) --------------|   Data chunks (8 per burst)
 *   |<------- xb (block) -------------|   4KB boundaries
 *   |<------- xe (end) ---------------|   End of file
 *   |-------- fX (result) ----------->|   Completion (closes meter)
 * </pre>
 *
 * <p><b>Critical:</b> Unlike downloads, uploads do NOT send xG. Client
 * immediately starts streaming data after receiving tf with 0x80 flag.
 */
public class XferUploadService {

    /** Default timeout waiting for each phase (30 seconds) */
    public static final long DEFAULT_PHASE_TIMEOUT_MS = 30_000;

    /** Default maximum file size (100 MB) */
    public static final int DEFAULT_MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;

    private final FileStorage fileStorage;
    private final long phaseTimeoutMs;
    private final int maxFileSizeBytes;

    public XferUploadService(FileStorage fileStorage) {
        this(fileStorage, DEFAULT_MAX_FILE_SIZE_BYTES, DEFAULT_PHASE_TIMEOUT_MS);
    }

    public XferUploadService(FileStorage fileStorage, int maxFileSizeBytes, long phaseTimeoutMs) {
        if (fileStorage == null) {
            throw new IllegalArgumentException("FileStorage cannot be null");
        }
        this.fileStorage = fileStorage;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.phaseTimeoutMs = phaseTimeoutMs;
    }

    /**
     * Initiate upload by sending th token to prompt file picker.
     *
     * <p>Sends TH_IN with a 2-byte response token. Client opens file picker
     * and responds with TH_OUT containing the selected filename.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param session session context for logging
     * @param registry upload registry for this connection
     * @return upload state in AWAITING_TH_RESPONSE phase
     * @throws IllegalStateException if an upload is already in progress
     */
    public XferUploadState initiateUpload(
            ChannelHandlerContext ctx,
            Pacer pacer,
            SessionContext session,
            XferUploadRegistry registry) {

        String username = session != null ? session.getDisplayName() : "unknown";

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] Initiating upload request", username));

        // Generate unique upload ID and 2-byte response token
        String uploadId = String.format("upload_%s_%d", username, System.currentTimeMillis());
        byte[] responseToken = generateResponseToken();

        // Create state
        XferUploadState state = new XferUploadState(
            uploadId, username, responseToken, maxFileSizeBytes);

        // Build and send th token
        byte[] thFrame = XferUploadFrameBuilder.buildThFrame(responseToken);
        pacer.enqueueSafe(ctx, thFrame, "XFER_TH_UPLOAD");

        LoggerUtil.debug(String.format(
            "[%s][XferUploadService] Sent th frame: %d bytes, token=[%02X %02X]",
            username, thFrame.length, responseToken[0] & 0xFF, responseToken[1] & 0xFF));

        // Schedule timeout for TH_OUT response
        ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(
            () -> registry.handleTimeout(),
            phaseTimeoutMs,
            TimeUnit.MILLISECONDS
        );
        state.setTimeoutFuture(timeoutFuture);

        // Register upload
        registry.registerPendingUpload(state);

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] Upload %s waiting for file selection (timeout: %dms)",
            username, uploadId, phaseTimeoutMs));

        return state;
    }

    /**
     * Handle TH_OUT response (client selected a file).
     *
     * <p>Processes the filename from TH_OUT and sends td token to request
     * file statistics (size, etc.).
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state upload state
     * @param registry upload registry
     * @param filename selected filename from TH_OUT
     */
    public void handleThResponse(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferUploadState state,
            XferUploadRegistry registry,
            String filename) {

        String username = state.getUsername();

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] File selected: %s", username, filename));

        // Cancel TH timeout
        state.cancelTimeout();

        // Store original path for TF frame (Windows needs absolute path to open file)
        state.setOriginalClientPath(filename);

        // Store filename for server-side storage (will be sanitized when written)
        state.setFilename(filename);

        // Transition to awaiting TD_OUT
        state.setPhase(XferUploadState.Phase.AWAITING_TD_RESPONSE);

        // Build and send td token to request file stats
        // Use TD-specific response token so client responds with "td" frame token
        byte[] tdResponseToken = generateTdResponseToken();
        byte[] tdFrame = XferUploadFrameBuilder.buildTdFrame(
            tdResponseToken,
            (byte) 0x01,  // field = size request
            filename       // Use original filename for client to find
        );
        pacer.enqueueSafe(ctx, tdFrame, "XFER_TD_UPLOAD");

        LoggerUtil.debug(String.format(
            "[%s][XferUploadService] Sent td frame: %d bytes, requesting stats for: %s",
            username, tdFrame.length, filename));

        // Schedule timeout for TD_OUT
        ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(
            () -> registry.handleTimeout(),
            phaseTimeoutMs,
            TimeUnit.MILLISECONDS
        );
        state.setTimeoutFuture(timeoutFuture);
    }

    /**
     * Handle TD_OUT response (file stats received).
     *
     * <p>Validates file size, opens output file, and sends tf token with
     * 0x80 flag to start the upload. Client will immediately begin streaming.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state upload state
     * @param registry upload registry
     * @param fileSize file size from TD_OUT (3-byte little-endian)
     * @param resultCode result code from TD_OUT (0x00 = success)
     */
    public void handleTdResponse(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferUploadState state,
            XferUploadRegistry registry,
            int fileSize,
            byte resultCode) {

        String username = state.getUsername();

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] TD_OUT: rc=0x%02X, size=%d bytes",
            username, resultCode & 0xFF, fileSize));

        // Cancel TD timeout
        state.cancelTimeout();

        // Check result code
        if (resultCode != 0x00) {
            sendFxResult(ctx, pacer, state, XferUploadFrameBuilder.FX_RESULT_FILE_NOT_FOUND,
                "Client could not access file");
            registry.markFailed("TD_OUT result code: 0x" + Integer.toHexString(resultCode & 0xFF));
            return;
        }

        // Check file size limit
        state.setExpectedSize(fileSize);
        if (state.exceedsMaxSize()) {
            sendFxResult(ctx, pacer, state, XferUploadFrameBuilder.FX_RESULT_FILE_TOO_LARGE,
                String.format("File too large (max %d MB)", maxFileSizeBytes / (1024 * 1024)));
            registry.markFailed("File exceeds max size: " + fileSize + " > " + maxFileSizeBytes);
            return;
        }

        // Prepare output file using storage abstraction
        try {
            WriteHandle writeHandle = fileStorage.write(
                FileStorage.Scope.USER,
                username,
                state.getFilename()
            );

            state.setTargetPath(writeHandle.path());
            state.setOutputStream(writeHandle.outputStream());
            // Update filename to actual (sanitized) filename
            state.setFilename(writeHandle.actualFilename());

        } catch (IOException e) {
            sendFxResult(ctx, pacer, state, XferUploadFrameBuilder.FX_RESULT_STORAGE_ERROR,
                "Server storage error");
            registry.markFailed("Failed to create output file: " + e.getMessage());
            return;
        }

        // Transition to awaiting data
        state.setPhase(XferUploadState.Phase.AWAITING_DATA);

        // Build and send tf token with upload flag (0x80)
        // Per A.1: No xG will be sent - client immediately starts streaming
        // CRITICAL: Use original client path for TF frame - Windows needs absolute path to open file
        byte[] tfFrame = XferUploadFrameBuilder.buildTfUploadFrame(fileSize, state.getOriginalClientPath());
        pacer.enqueueSafe(ctx, tfFrame, "XFER_TF_UPLOAD");

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] Sent tf frame with 0x80 flag, starting upload: %s (%d bytes)",
            username, state.getOriginalClientPath(), fileSize));

        // No timeout for data phase - client drives sending
        // If connection drops, registry.close() will handle cleanup
    }

    /**
     * Handle xd token (data chunk).
     *
     * <p>Decodes escape-encoded data and writes to file.
     *
     * @param ctx channel context (for potential flow control)
     * @param state upload state
     * @param registry upload registry
     * @param encodedData escape-encoded bytes from xd packet
     */
    public void handleDataChunk(
            ChannelHandlerContext ctx,
            XferUploadState state,
            XferUploadRegistry registry,
            byte[] encodedData) {

        // Transition from AWAITING_DATA to RECEIVING_DATA on first chunk
        if (state.getPhase() == XferUploadState.Phase.AWAITING_DATA) {
            state.setPhase(XferUploadState.Phase.RECEIVING_DATA);
            LoggerUtil.debug(String.format(
                "[%s][XferUploadService] First data chunk received, now in RECEIVING_DATA",
                state.getUsername()));
        }

        try {
            // Decode escape-encoded data
            byte[] decodedData = XferEncoder.decode(encodedData);

            // Check size limit
            state.addReceivedBytes(decodedData.length);
            if (state.exceedsMaxSize()) {
                throw new IOException("File size exceeded limit: " + state.getReceivedBytes() +
                    " > " + state.getMaxFileSizeBytes());
            }

            // Write to file
            OutputStream out = state.getOutputStream();
            if (out != null) {
                out.write(decodedData);
            }

        } catch (IOException e) {
            LoggerUtil.error(String.format(
                "[%s][XferUploadService] Write error: %s",
                state.getUsername(), e.getMessage()));
            // Don't send fX here - let caller handle error recovery
            registry.markFailed("Write error: " + e.getMessage());
        }
    }

    /**
     * Handle xb token (block boundary - 4KB marker).
     *
     * <p>Same as xd but indicates 4KB block boundary. Log progress.
     *
     * @param ctx channel context
     * @param state upload state
     * @param registry upload registry
     * @param encodedData escape-encoded bytes from xb packet
     */
    public void handleBlockMarker(
            ChannelHandlerContext ctx,
            XferUploadState state,
            XferUploadRegistry registry,
            byte[] encodedData) {

        // Process data same as xd
        handleDataChunk(ctx, state, registry, encodedData);

        // Log progress
        LoggerUtil.debug(String.format(
            "[%s][XferUploadService] Block marker: %d/%d bytes received (%d%%)",
            state.getUsername(),
            state.getReceivedBytes(),
            state.getExpectedSize(),
            state.getProgressPercent()));
    }

    /**
     * Handle xe token (end of file).
     *
     * <p>Processes final data (if any), closes file, validates, and sends fX result.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state upload state
     * @param registry upload registry
     * @param encodedData final escape-encoded bytes (may be empty)
     */
    public void handleEndOfFile(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferUploadState state,
            XferUploadRegistry registry,
            byte[] encodedData) {

        String username = state.getUsername();

        LoggerUtil.info(String.format(
            "[%s][XferUploadService] End of file received", username));

        try {
            // Process final data if present
            if (encodedData != null && encodedData.length > 0) {
                byte[] decodedData = XferEncoder.decode(encodedData);
                state.addReceivedBytes(decodedData.length);

                OutputStream out = state.getOutputStream();
                if (out != null) {
                    out.write(decodedData);
                }
            }

            // Close output stream
            if (state.getOutputStream() != null) {
                state.getOutputStream().close();
            }

            // Log size comparison
            if (state.getReceivedBytes() != state.getExpectedSize()) {
                LoggerUtil.warn(String.format(
                    "[%s][XferUploadService] Size mismatch: expected=%d, received=%d",
                    username, state.getExpectedSize(), state.getReceivedBytes()));
            }

            // Send success fX
            sendFxResult(ctx, pacer, state, XferUploadFrameBuilder.FX_RESULT_SUCCESS,
                "Upload complete");

            registry.markCompleted();

            LoggerUtil.info(String.format(
                "[%s][XferUploadService] Upload complete: %s (%d bytes in %dms)",
                username, state.getFilename(), state.getReceivedBytes(), state.getElapsedMs()));

        } catch (IOException e) {
            sendFxResult(ctx, pacer, state, XferUploadFrameBuilder.FX_RESULT_STORAGE_ERROR,
                "Server error on close");
            registry.markFailed("Close error: " + e.getMessage());
        }
    }

    /**
     * Handle xK token (client abort).
     *
     * <p>Client cancelled or encountered error. Cleanup and send fX acknowledgment.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state upload state
     * @param registry upload registry
     * @param reasonCode abort reason code from xK packet
     */
    public void handleAbort(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferUploadState state,
            XferUploadRegistry registry,
            byte reasonCode) {

        String username = state.getUsername();

        LoggerUtil.warn(String.format(
            "[%s][XferUploadService] Upload aborted by client: reason=0x%02X",
            username, reasonCode & 0xFF));

        // Send fX with abort acknowledgment
        String message = switch (reasonCode & 0xFF) {
            case 0xFE -> "User cancelled";
            case 0xFF -> "File read error";
            case 0xFD -> "Transfer cancelled";
            default -> "Upload cancelled";
        };
        sendFxResult(ctx, pacer, state, reasonCode, message);

        registry.markAborted(reasonCode);
    }

    /**
     * Send fX result token.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state upload state
     * @param resultCode result code (0x00 = success)
     * @param message human-readable message
     */
    private void sendFxResult(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferUploadState state,
            byte resultCode,
            String message) {

        byte[] fxFrame = XferUploadFrameBuilder.buildFxFrame(resultCode, message);
        pacer.enqueueSafe(ctx, fxFrame, "XFER_FX");

        LoggerUtil.debug(String.format(
            "[%s][XferUploadService] Sent fX: rc=0x%02X, msg=%s",
            state.getUsername(), resultCode & 0xFF, message));
    }

    /**
     * Generate fixed 2-byte response token based on the phase.
     *
     * <p>Uses fixed tokens 'th' (0x74, 0x68) for TH phase so the switch
     * statement in StatefulClientHandler can match the incoming TH_OUT
     * response. The client echoes TH_IN.token[0-1] as the P3 frame token.
     */
    private byte[] generateResponseToken() {
        // Use 'th' (0x74, 0x68) so client response matches switch case "th"
        return new byte[] { 0x74, 0x68 };
    }

    /**
     * Generate fixed 2-byte response token for TD phase.
     *
     * <p>Uses fixed tokens 'td' (0x74, 0x64) for TD phase so the switch
     * statement in StatefulClientHandler can match the incoming TD_OUT
     * response.
     */
    private byte[] generateTdResponseToken() {
        // Use 'td' (0x74, 0x64) so client response matches switch case "td"
        return new byte[] { 0x74, 0x64 };
    }

    /**
     * Get file storage for direct access.
     */
    public FileStorage getFileStorage() {
        return fileStorage;
    }

    /**
     * Get maximum file size in bytes.
     */
    public int getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
