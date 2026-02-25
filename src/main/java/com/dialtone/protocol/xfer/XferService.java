/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.xfer;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.XferAnnounceFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing XFER file transfers with proper xG handshake.
 *
 * <p>Orchestrates the XFER protocol with correct client acknowledgment flow:
 * <ol>
 *   <li>Phase 1 (Atom Phase): Send xfer atoms via FDO to announce the file</li>
 *   <li>Phase 2a (Token Phase): Send tj + tf tokens (file metadata)</li>
 *   <li>Wait for xG acknowledgment from client</li>
 *   <li>Phase 2b (Data Phase): Send F7/F8/F9 frames with file data</li>
 * </ol>
 *
 * <p><b>Protocol Flow:</b>
 * <pre>
 * Server                              Client
 *   |-------- xfer_announce.fdo -------->|
 *   |-------- tj (file description) ---->|
 *   |-------- tf (start transfer) ------>|
 *   |                                    |
 *   |                                    | (Client opens destination file)
 *   |                                    |
 *   |<----------- xG -------------------|
 *   |                                    |
 *   |-------- F7/F8 (data chunks) ------>|
 *   |-------- F9 (final chunk) --------->|
 * </pre>
 */
public class XferService {

    private static final int MAX_BURST_FRAMES = 4;

    /** Chunk size for encoded data in each F7/F9 frame (bytes) */
    private static final int CHUNK_SIZE = 950;

    /** Default timeout waiting for xG acknowledgment (30 seconds) */
    public static final long DEFAULT_XG_TIMEOUT_MS = 30_000;

    private final FdoCompiler fdoCompiler;
    private final long xgTimeoutMs;

    public XferService(FdoCompiler fdoCompiler) {
        this(fdoCompiler, DEFAULT_XG_TIMEOUT_MS);
    }

    public XferService(FdoCompiler fdoCompiler, long xgTimeoutMs) {
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        this.fdoCompiler = fdoCompiler;
        this.xgTimeoutMs = xgTimeoutMs;
    }

    /**
     * Initiate a file transfer (Phases 1 and 2a).
     *
     * <p>Sends xfer atoms, tj, and tf tokens, then registers for xG wait.
     * Data frames will be sent after xG is received via {@link #resumeAfterXg}.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param filename short filename (e.g., "tiny.txt")
     * @param fileData raw file data
     * @param session session context for logging
     * @param registry transfer registry for this connection
     * @return transfer state in AWAITING_XG phase
     * @throws IllegalStateException if a transfer is already pending
     */
    public XferTransferState initiateTransfer(
            ChannelHandlerContext ctx,
            Pacer pacer,
            String filename,
            byte[] fileData,
            SessionContext session,
            XferTransferRegistry registry) {

        String username = session != null ? session.getDisplayName() : "unknown";
        int fileSize = fileData != null ? fileData.length : 0;

        LoggerUtil.info(String.format(
            "[%s][XferService] Initiating file transfer: %s (%d bytes)",
            username, filename, fileSize));

        // Generate file metadata
        byte[] fileId = generateFileId();
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        int requestId = ThreadLocalRandom.current().nextInt(100000, 999999);
        String transferId = String.format("xfer_%s_%d", filename, timestamp);

        // Pre-encode data for efficiency (won't change)
        byte[] encodedData = (fileData != null && fileData.length > 0)
            ? XferEncoder.encode(fileData)
            : new byte[0];

        // Create transfer state (pass fileSize instead of fileData so raw data becomes GC-eligible)
        XferTransferState state = new XferTransferState(
            transferId, filename, fileSize, fileId, encodedData, timestamp, username);

        try {
            // Phase 1: Send xfer atoms via FDO
            sendPhase1Atoms(ctx, pacer, username, filename, fileSize, requestId, timestamp);

            // Phase 2a: Send tj and tf tokens (but NOT data)
            sendTjTfTokens(ctx, pacer, username, filename, fileId, fileSize, timestamp);

            // Schedule timeout for xG wait
            ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(
                () -> registry.handleTimeout(),
                xgTimeoutMs,
                TimeUnit.MILLISECONDS
            );
            state.setTimeoutFuture(timeoutFuture);

            // Register as awaiting xG
            registry.registerPendingTransfer(state);

            LoggerUtil.info(String.format(
                "[%s][XferService] Transfer %s waiting for xG (timeout: %dms)",
                username, transferId, xgTimeoutMs));

            return state;

        } catch (Exception e) {
            state.setPhase(XferTransferState.Phase.FAILED);
            LoggerUtil.error(String.format(
                "[%s][XferService] Transfer initiation failed: %s - %s",
                username, filename, e.getMessage()));
            throw new RuntimeException("Transfer initiation failed", e);
        }
    }

    /**
     * Resume transfer after xG acknowledgment received.
     *
     * <p>Called by StatefulClientHandler when xG token arrives.
     * Sends the F7/F8/F9 data frames to complete the transfer.
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state transfer state from registry
     * @param registry transfer registry (for marking complete)
     */
    public void resumeAfterXg(
            ChannelHandlerContext ctx,
            Pacer pacer,
            XferTransferState state,
            XferTransferRegistry registry) {

        String username = state.getUsername();

        LoggerUtil.info(String.format(
            "[%s][XferService] Resuming transfer %s after xG (waited %dms)",
            username, state.getTransferId(), state.getElapsedMs()));

        // Cancel timeout
        state.cancelTimeout();
        state.setPhase(XferTransferState.Phase.SENDING_DATA);

        try {
            // Phase 2b: Send data frames
            sendDataFrames(ctx, pacer, state);

            // Mark complete
            registry.markCompleted();

            LoggerUtil.info(String.format(
                "[%s][XferService] Transfer %s complete (total time: %dms)",
                username, state.getTransferId(), state.getElapsedMs()));

        } catch (Exception e) {
            registry.markFailed("Data send failed: " + e.getMessage());
            LoggerUtil.error(String.format(
                "[%s][XferService] Transfer %s failed during data send: %s",
                username, state.getTransferId(), e.getMessage()));
        }
    }

    /**
     * Legacy synchronous method - sends all frames without waiting for xG.
     *
     * @deprecated Use {@link #initiateTransfer} + {@link #resumeAfterXg} for proper handshake
     */
    @Deprecated
    public void transferFile(ChannelHandlerContext ctx, Pacer pacer,
                             String filename, byte[] fileData, SessionContext session) {
        String username = session != null ? session.getDisplayName() : "unknown";
        int fileSize = fileData != null ? fileData.length : 0;

        LoggerUtil.info(String.format(
            "[%s][XferService] Starting file transfer (LEGACY - no xG wait): %s (%d bytes)",
            username, filename, fileSize));

        try {
            // Generate file metadata
            byte[] fileId = generateFileId();
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            int requestId = ThreadLocalRandom.current().nextInt(100000, 999999);

            // Phase 1: Send xfer atoms via FDO
            sendPhase1Atoms(ctx, pacer, username, filename, fileSize, requestId, timestamp);

            // Phase 2: Send binary token frames (WITHOUT waiting for xG)
            sendTjTfTokens(ctx, pacer, username, filename, fileId, fileSize, timestamp);

            // Immediately send data (LEGACY - no xG wait)
            byte[] encodedData = (fileData != null && fileData.length > 0)
                ? XferEncoder.encode(fileData)
                : new byte[0];
            sendDataFramesLegacy(ctx, pacer, username, filename, fileData, encodedData);

            LoggerUtil.info(String.format(
                "[%s][XferService] File transfer complete (LEGACY): %s",
                username, filename));

        } catch (Exception e) {
            LoggerUtil.error(String.format(
                "[%s][XferService] File transfer failed: %s - %s",
                username, filename, e.getMessage()));
            throw new RuntimeException("File transfer failed", e);
        }
    }

    /**
     * Phase 1: Send XFER atoms via FDO template to announce the file.
     */
    private void sendPhase1Atoms(ChannelHandlerContext ctx, Pacer pacer, String username,
                                  String filename, int fileSize, int requestId, int timestamp) {
        LoggerUtil.debug(String.format(
            "[%s][XferService] Phase 1: Sending xfer atoms for %s",
            username, filename));

        try {
            // Use DSL builder for xfer announcement (replaces fdo/xfer_announce.fdo.txt)
            XferAnnounceFdoBuilder xferBuilder = XferAnnounceFdoBuilder.announce(
                "Applications",
                requestId,
                "File Download: " + filename,
                fileSize,
                filename,
                timestamp
            );
            String xferFdoSource = xferBuilder.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                xferFdoSource,
                "AT",
                FdoCompiler.AUTO_GENERATE_STREAM_ID
            );

            if (chunks != null && !chunks.isEmpty()) {
                P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "XFER_ATOMS", MAX_BURST_FRAMES, username);
                LoggerUtil.debug(String.format(
                    "[%s][XferService] Phase 1 complete: %d chunks sent",
                    username, chunks.size()));
            } else {
                LoggerUtil.warn(String.format(
                    "[%s][XferService] Phase 1: No chunks generated from FDO template",
                    username));
            }
        } catch (Exception e) {
            LoggerUtil.warn(String.format(
                "[%s][XferService] Phase 1 failed (continuing with Phase 2): %s",
                username, e.getMessage()));
            // Continue with Phase 2 even if Phase 1 fails
            // The client may still accept the transfer via tokens only
        }
    }

    /**
     * Phase 2a: Send tj and tf tokens only (stop before data).
     */
    private void sendTjTfTokens(ChannelHandlerContext ctx, Pacer pacer, String username,
                                 String filename, byte[] fileId, int fileSize, int timestamp) {
        LoggerUtil.debug(String.format(
            "[%s][XferService] Phase 2a: Sending tj/tf tokens for %s",
            username, filename));

        // 1. Send tj token (file description)
        byte[] tjFrame = XferFrameBuilder.buildTjFrame(
            0x00,           // file type
            fileId,
            timestamp,
            fileSize,
            "Applications", // library
            "File Download: " + filename  // subject
        );
        pacer.enqueueSafe(ctx, tjFrame, "XFER_TJ");
        LoggerUtil.debug(String.format(
            "[%s][XferService] Sent tj frame: %d bytes",
            username, tjFrame.length));

        // 2. Send tf token (start transfer)
        byte[] tfFrame = XferFrameBuilder.buildTfFrame(
            XferFrameBuilder.TF_FLAG_PROGRESS_METER,
            fileSize,
            timestamp,  // modTime
            timestamp,  // createTime
            filename
        );
        pacer.enqueueSafe(ctx, tfFrame, "XFER_TF");
        LoggerUtil.debug(String.format(
            "[%s][XferService] Sent tf frame: %d bytes - now awaiting xG",
            username, tfFrame.length));
    }

    /**
     * Phase 2b: Send data frames (F7/F8/F9) after xG received.
     *
     * <p>For small files (&lt;= CHUNK_SIZE), sends a single F9 frame.
     * For large files, splits into F7 (intermediate) chunks + F9 (final).
     */
    private void sendDataFrames(ChannelHandlerContext ctx, Pacer pacer, XferTransferState state) {
        String username = state.getUsername();
        byte[] encodedData = state.getEncodedData();

        LoggerUtil.debug(String.format(
            "[%s][XferService] Phase 2b: Sending data frames for %s",
            username, state.getFilename()));

        if (encodedData == null || encodedData.length == 0) {
            // Empty file - still send F9 to complete transfer
            byte[] f9Frame = XferFrameBuilder.buildDataFrame(new byte[0], true);
            pacer.enqueueSafe(ctx, f9Frame, "XFER_F9_EMPTY");
            LoggerUtil.debug(String.format("[%s][XferService] Sent empty F9 frame", username));
            return;
        }

        int totalChunks = (encodedData.length + CHUNK_SIZE - 1) / CHUNK_SIZE;

        if (totalChunks == 1) {
            // Small file - single F9 (existing behavior)
            byte[] f9Frame = XferFrameBuilder.buildDataFrame(encodedData, true);
            pacer.enqueueSafe(ctx, f9Frame, "XFER_F9");
            LoggerUtil.debug(String.format(
                "[%s][XferService] Sent F9 frame: %d bytes (encoded from %d)",
                username, f9Frame.length, state.getFileSize()));
        } else {
            // Large file - chunked F7/F9 frames
            sendChunkedDataFrames(ctx, pacer, state, encodedData, totalChunks);
        }
    }

    /**
     * Send chunked data frames for large files.
     *
     * <p>Splits encoded data into CHUNK_SIZE chunks:
     * <ul>
     *   <li>F7 for all intermediate chunks</li>
     *   <li>F9 for the final chunk</li>
     * </ul>
     *
     * @param ctx channel context
     * @param pacer frame pacer
     * @param state transfer state
     * @param encodedData pre-encoded file data
     * @param totalChunks total number of chunks to send
     */
    private void sendChunkedDataFrames(ChannelHandlerContext ctx, Pacer pacer,
                                       XferTransferState state, byte[] encodedData, int totalChunks) {
        String username = state.getUsername();
        int offset = 0;

        LoggerUtil.info(String.format(
            "[%s][XferService] Sending large file in %d chunks: %s (%d bytes encoded)",
            username, totalChunks, state.getFilename(), encodedData.length));

        for (int chunkNum = 0; chunkNum < totalChunks; chunkNum++) {
            boolean isLast = (chunkNum == totalChunks - 1);
            int chunkLen = Math.min(CHUNK_SIZE, encodedData.length - offset);

            byte[] chunkData = Arrays.copyOfRange(encodedData, offset, offset + chunkLen);
            byte[] frame = XferFrameBuilder.buildDataFrame(chunkData, isLast);

            String label = isLast
                ? String.format("XFER_F9[%d/%d]", chunkNum + 1, totalChunks)
                : String.format("XFER_F7[%d/%d]", chunkNum + 1, totalChunks);

            pacer.enqueueSafe(ctx, frame, label);
            offset += chunkLen;

            // Log progress every 100 chunks for visibility on large files
            if ((chunkNum + 1) % 100 == 0 || isLast) {
                LoggerUtil.debug(String.format(
                    "[%s][XferService] Progress: %d/%d chunks queued (%.1f%%)",
                    username, chunkNum + 1, totalChunks,
                    (chunkNum + 1) * 100.0 / totalChunks));
            }
        }

        LoggerUtil.info(String.format(
            "[%s][XferService] Queued %d data frames (%d bytes encoded, %d bytes raw)",
            username, totalChunks, encodedData.length, state.getFileSize()));
    }

    /**
     * Legacy data frame sending (for deprecated transferFile method).
     */
    private void sendDataFramesLegacy(ChannelHandlerContext ctx, Pacer pacer, String username,
                                       String filename, byte[] fileData, byte[] encodedData) {
        if (encodedData != null && encodedData.length > 0) {
            byte[] f9Frame = XferFrameBuilder.buildDataFrame(encodedData, true);
            pacer.enqueueSafe(ctx, f9Frame, "XFER_F9");
            LoggerUtil.debug(String.format(
                "[%s][XferService] Sent F9 frame: %d bytes (encoded from %d)",
                username, f9Frame.length, fileData.length));
        } else {
            byte[] f9Frame = XferFrameBuilder.buildDataFrame(new byte[0], true);
            pacer.enqueueSafe(ctx, f9Frame, "XFER_F9_EMPTY");
            LoggerUtil.debug(String.format(
                "[%s][XferService] Sent empty F9 frame",
                username));
        }
    }

    /**
     * Generate a random 3-byte file ID.
     */
    private byte[] generateFileId() {
        byte[] id = new byte[3];
        ThreadLocalRandom.current().nextBytes(id);
        return id;
    }
}
