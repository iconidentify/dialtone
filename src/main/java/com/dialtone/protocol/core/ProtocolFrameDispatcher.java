/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.core;

import com.dialtone.aol.core.FrameCodec;
import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.MultiFrameStreamProcessor;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.PacketParser;
import com.dialtone.protocol.PacketType;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.dod.DODTokenBehavior;
import com.dialtone.protocol.dod.DodRequestHandler;
import com.dialtone.protocol.im.IMTokenBehavior;
import com.dialtone.protocol.keyword.KeywordProcessor;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.protocol.xfer.XferUploadRegistry;
import com.dialtone.protocol.xfer.XferUploadState;
import com.dialtone.protocol.xfer.XferUploadFrameBuilder;
import com.dialtone.protocol.xfer.XferTransferState;
import com.dialtone.protocol.chat.ChatTokenHandler;
import com.dialtone.protocol.im.InstantMessageTokenHandler;
import com.dialtone.protocol.auth.LoginTokenHandler;
import com.dialtone.protocol.skalholt.SkalholtTokenHandler;
import com.dialtone.protocol.news.NewsTokenHandler;
import com.dialtone.protocol.tos.TosTokenHandler;
import com.dialtone.protocol.dod.DodTokenHandler;
import com.dialtone.filebrowser.FileBrowserTokenHandler;
import com.dialtone.state.SequenceManager;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates token routing and delegates to appropriate token handlers.
 * Manages multi-frame stream accumulation and handles tokens not handled by specific handlers.
 */
public class ProtocolFrameDispatcher {
    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final DodRequestHandler dodRequestHandler;
    private final XferService xferService;
    private final XferTransferRegistry xferRegistry;
    private final XferUploadService xferUploadService;
    private final XferUploadRegistry xferUploadRegistry;
    private final String logPrefix;
    
    // Upload flow control flags (from application.properties)
    private final boolean uploadTnEnabled;
    private final boolean uploadAckEnabled;
    
    // Disconnect handler for pE token (logout)
    private final DisconnectHandler disconnectHandler;

    // Token handlers
    private final ChatTokenHandler chatHandler;
    private final InstantMessageTokenHandler imHandler;
    private final LoginTokenHandler loginHandler;
    private final SkalholtTokenHandler skalholtHandler;
    private final NewsTokenHandler newsHandler;
    private final TosTokenHandler tosHandler;
    private final DodTokenHandler dodHandler;
    private final FileBrowserTokenHandler fileBrowserHandler;

    // Multi-frame stream accumulation maps (for tokens not yet extracted)
    private final Map<Integer, List<byte[]>> pendingKkStreams = new ConcurrentHashMap<>();

    public ProtocolFrameDispatcher(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                                   FdoProcessor fdoProcessor, DodRequestHandler dodRequestHandler,
                                   XferService xferService, XferTransferRegistry xferRegistry,
                                   XferUploadService xferUploadService, XferUploadRegistry xferUploadRegistry,
                                   ChatTokenHandler chatHandler, InstantMessageTokenHandler imHandler,
                                   LoginTokenHandler loginHandler, SkalholtTokenHandler skalholtHandler,
                                   NewsTokenHandler newsHandler, TosTokenHandler tosHandler,
                                   DodTokenHandler dodHandler, FileBrowserTokenHandler fileBrowserHandler,
                                   DisconnectHandler disconnectHandler) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fdoProcessor = fdoProcessor;
        this.dodRequestHandler = dodRequestHandler;
        this.xferService = xferService;
        this.xferRegistry = xferRegistry;
        this.xferUploadService = xferUploadService;
        this.xferUploadRegistry = xferUploadRegistry;
        this.chatHandler = chatHandler;
        this.imHandler = imHandler;
        this.loginHandler = loginHandler;
        this.skalholtHandler = skalholtHandler;
        this.newsHandler = newsHandler;
        this.tosHandler = tosHandler;
        this.dodHandler = dodHandler;
        this.fileBrowserHandler = fileBrowserHandler;
        this.disconnectHandler = disconnectHandler;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
        
        // Load upload flow control flags from properties
        java.util.Properties props = loadApplicationProperties();
        this.uploadTnEnabled = Boolean.parseBoolean(props.getProperty("upload.tn.enabled", "false"));
        this.uploadAckEnabled = Boolean.parseBoolean(props.getProperty("upload.ack.enabled", "true"));
    }
    
    /**
     * Interface for disconnect operations (to avoid circular dependency with StatefulClientHandler).
     */
    public interface DisconnectHandler {
        void handleDisconnect(ChannelHandlerContext ctx, String message);
    }
    
    private static java.util.Properties loadApplicationProperties() {
        java.util.Properties props = new java.util.Properties();
        try (var in = ProtocolFrameDispatcher.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            LoggerUtil.warn("Failed to load application.properties, using defaults: " + e.getMessage());
        }
        return props;
    }

    /**
     * Dispatch a single frame to the appropriate handler.
     */
    public void dispatchToken(ChannelHandlerContext ctx, byte[] in, SequenceManager sequenceManager) throws Exception {
        if (!isValidFrame(in)) return;

        // Handle short control frames (9B)
        if (handleShortControl9B(ctx, in, sequenceManager)) return;

        final String token = FrameCodec.extractTokenAscii(in);

        // Null token (init packets, etc.) - handled by StatefulClientHandler.handleNullToken()
        if (token == null) {
            LoggerUtil.debug(() -> logPrefix + String.format("TOKEN: null (frame length=%d)", in.length));
            return;
        }
        // Note: Token logging is done in StatefulClientHandler.dispatchSingleFrame()

        if (PacketParser.isFiveA(in) && in.length == ProtocolConstants.SHORT_FRAME_SIZE) return;

        final PacketParser.ParsedFrame frame = PacketParser.parse(in);

        final int type = in[ProtocolConstants.IDX_TYPE] & ProtocolConstants.BYTE_MASK;
        if (type == PacketType.A3.getValue()
                && (in[ProtocolConstants.IDX_TOKEN] & 0xFF) == 0x0C
                && (in[ProtocolConstants.IDX_TOKEN + 1] & 0xFF) == 0x03) {
            sequenceManager.initializeFromClientProbe(in);
        }

        // Route to appropriate handler
        if (chatHandler.canHandle(token)) {
            chatHandler.handle(ctx, in, session);
        } else if (imHandler.canHandle(token)) {
            imHandler.handle(ctx, in, session);
        } else if (loginHandler.canHandle(token)) {
            loginHandler.handle(ctx, in, session);
        } else if (skalholtHandler.canHandle(token)) {
            skalholtHandler.handle(ctx, in, session);
        } else if (newsHandler.canHandle(token)) {
            newsHandler.handle(ctx, in, session);
        } else if (tosHandler.canHandle(token)) {
            tosHandler.handle(ctx, in, session);
        } else if (dodHandler.canHandle(token)) {
            dodHandler.handle(ctx, in, session);
        } else if (fileBrowserHandler != null && fileBrowserHandler.canHandle(token)) {
            fileBrowserHandler.handle(ctx, in, session);
        } else {
            // Handle tokens not yet extracted to handlers
            handleRemainingTokens(ctx, in, token);
        }
    }

    /**
     * Handle tokens that haven't been extracted to specific handlers yet.
     */
    private void handleRemainingTokens(ChannelHandlerContext ctx, byte[] in, String token) throws Exception {
        switch (token) {
            case "LO":
                ctx.disconnect();
                break;

            case "SF":
            case "ff":
                // UI control frame
                LoggerUtil.debug(logPrefix + "ff control frame - queuing ACK for drain");
                byte[] resp = buildShortControl(0x24);
                pacer.enqueuePrioritySafe(ctx, resp, "FF_CTRL_ACK");
                break;

            case "fh":
                // fh token is now handled by DodTokenHandler via canHandle() routing above
                // This case should not be reached, but kept for safety
                LoggerUtil.warn(logPrefix + "fh DOD token not handled by DodTokenHandler - routing issue");
                break;

            case "f2":
            case "f1":
            case "K1":
                // DOD tokens are now handled by DodTokenHandler via canHandle() routing above
                // This case should not be reached, but kept for safety
                LoggerUtil.warn(logPrefix + "DOD token " + token + " not handled by DodTokenHandler - routing issue");
                break;

            case "pE":
                // pE token: User-initiated logout/disconnect
                LoggerUtil.info(logPrefix + "pE (logout) token received - disconnecting user");
                disconnectHandler.handleDisconnect(ctx, "Thank you for using Dialtone!");
                break;

            case "Kk":
                // Keyword - handled by KeywordProcessor
                try {
                    int streamId = MultiFrameStreamProcessor.extractStreamId(in);

                    if (MultiFrameStreamProcessor.isUniEndStream(in, fdoCompiler)) {
                        List<byte[]> accumulatedFrames = pendingKkStreams.get(streamId);

                        String keyword;
                        if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                            List<byte[]> allFrames = new ArrayList<>(accumulatedFrames);
                            allFrames.add(in);
                            keyword = MultiFrameStreamProcessor.extractDeDataFromMultiFrame(allFrames, fdoCompiler, "Kk");
                            pendingKkStreams.remove(streamId);
                        } else {
                            keyword = MultiFrameStreamProcessor.extractDeDataFromSingleFrame(in, fdoCompiler, "Kk");
                        }

                        boolean handled = KeywordProcessor.processKeyword(keyword, session, ctx, pacer);

                        if (!handled) {
                            LoggerUtil.info(logPrefix + "Unknown keyword - sending control ACK to prevent client hang");
                            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "KK_UNKNOWN_ACK");
                        }
                    } else {
                        pendingKkStreams.computeIfAbsent(streamId, k -> new ArrayList<>()).add(
                                Arrays.copyOf(in, in.length)
                        );
                    }
                } catch (Exception e) {
                    LoggerUtil.error(logPrefix + "Failed to process Kk: " + e.getMessage());
                    fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "KK_UNKNOWN_ACK");
                }
                break;

            case "xG":
                // xG token: Client acknowledgment that file transfer destination is ready
                handleXgToken(ctx, in);
                break;

            case "th":
                // th token: TH_OUT response - client selected a file for upload
                handleThOutToken(ctx, in);
                break;

            case "td":
                // td token: TD_OUT response - client returned file stats
                handleTdOutToken(ctx, in);
                break;

            case "xd":
                // xd token: Upload data chunk (escape-encoded)
                handleXdToken(ctx, in);
                break;

            case "xb":
                // xb token: Upload block boundary (4KB marker with data)
                handleXbToken(ctx, in);
                break;

            case "xe":
                // xe token: End of file (final data chunk)
                handleXeToken(ctx, in);
                break;

            case "xK":
                // xK token: Client abort (user cancelled or read error)
                handleXKToken(ctx, in);
                break;

            default:
                LoggerUtil.debug(logPrefix + "Ignoring unrecognized token: " + token);
                fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "ACK");
                break;
        }
    }

    /**
     * Handle short control frames (9B type).
     */
    private boolean handleShortControl9B(ChannelHandlerContext ctx, byte[] in, SequenceManager sequenceManager) {
        if (!PacketParser.isFiveA(in) || in.length != ProtocolConstants.SHORT_FRAME_SIZE) return false;

        int type = in[ProtocolConstants.IDX_TYPE] & ProtocolConstants.BYTE_MASK;

        if (PacketType.isAolAcknowledgmentType(type, PacketType.A4)) {
            pacer.onA4WindowOpenNoDrain();
            return true;
        }

        if (PacketType.isAolAcknowledgmentType(type, PacketType.A5)) {
            pacer.onA5KeepAliveNoDrain();
            return true;
        }

        if (type == 0xA6) {
            // Heartbeat - respond with ACK
            LoggerUtil.info(logPrefix + "Ping received (0xA6) - queuing ACK (0x24) for drain");
            byte[] pong = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, pong, "PING_PONG");
            return true;
        }

        PacketType responseType = PacketType.getAcknowledgmentResponseType(type);
        if (responseType != null) {
            byte[] responseFrame = buildShortControl(responseType.getValue());
            pacer.enqueuePrioritySafe(ctx, responseFrame, "9B_ECHO");
        }
        return true;
    }

    /**
     * Validate frame structure.
     */
    private static boolean isValidFrame(byte[] in) {
        try {
            FrameCodec.parse(in);
            return true;
        } catch (Exception ex) {
            String token = "??";
            int type = -1;
            int tx = -1;
            int rx = -1;

            try {
                if (in != null && in.length >= 10) {
                    token = new String(new byte[]{in[8], in[9]}, StandardCharsets.US_ASCII);
                }
                if (in != null && in.length >= 8) {
                    type = in[7] & 0xFF;
                }
                if (in != null && in.length >= 7) {
                    tx = in[5] & 0xFF;
                    rx = in[6] & 0xFF;
                }
            } catch (Exception ignored) {
            }

            LoggerUtil.warn(String.format("[frame] Drop invalid frame: token=%s type=0x%02X tx=0x%02X rx=0x%02X error=%s",
                    token, type, tx, rx, ex.getMessage()));
            return false;
        }
    }

    /**
     * Build short control frame.
     */
    private byte[] buildShortControl(int type) {
        byte[] b = new byte[ProtocolConstants.SHORT_FRAME_SIZE];
        b[0] = (byte) ProtocolConstants.AOL_FRAME_MAGIC;
        b[ProtocolConstants.IDX_LEN_HI] = 0x00;
        b[ProtocolConstants.IDX_LEN_LO] = 0x03;
        b[ProtocolConstants.IDX_TYPE] = (byte) (type & 0xFF);
        b[ProtocolConstants.IDX_TOKEN] = 0x0D;
        return b;
    }

    /**
     * Get pending Kk streams map (for integration with StatefulClientHandler during migration).
     */
    public Map<Integer, List<byte[]>> getPendingKkStreams() {
        return pendingKkStreams;
    }
    
    // =========================================================================
    // File Transfer Token Handlers
    // =========================================================================
    
    /**
     * Handles xG token: Client acknowledgment that file transfer destination is ready.
     */
    private void handleXgToken(ChannelHandlerContext ctx, byte[] in) {
        LoggerUtil.info(logPrefix + "xG token received - client ready to receive file data");

        if (xferRegistry == null) {
            LoggerUtil.error(logPrefix + "xG received but XferTransferRegistry not initialized");
            return;
        }

        if (xferService == null) {
            LoggerUtil.error(logPrefix + "xG received but XferService not initialized");
            return;
        }

        XferTransferState transfer = xferRegistry.onXgReceived();
        if (transfer == null) {
            LoggerUtil.warn(logPrefix + "xG received but no pending transfer - sending ACK to prevent client hang");
            try {
                fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "XG_NO_TRANSFER");
            } catch (Exception e) {
                LoggerUtil.error(logPrefix + "Failed to send xG ACK: " + e.getMessage());
            }
            return;
        }

        // Resume the transfer - send data frames
        xferService.resumeAfterXg(ctx, pacer, transfer, xferRegistry);
    }
    
    /**
     * Handles TH_OUT token: Client response to our th prompt with selected filename.
     */
    private void handleThOutToken(ChannelHandlerContext ctx, byte[] in) {
        LoggerUtil.info(logPrefix + "TH_OUT token received - client selected file for upload");

        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "TH_OUT received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.warn(logPrefix + "TH_OUT received but no pending upload");
            return;
        }

        if (state.getPhase() != XferUploadState.Phase.AWAITING_TH_RESPONSE) {
            LoggerUtil.warn(logPrefix + "TH_OUT received but upload in wrong phase: " + state.getPhase());
            return;
        }

        // Parse TH_OUT struct from payload (after 10-byte P3 header)
        int payloadOffset = 10;
        if (in.length < payloadOffset + 1) {
            LoggerUtil.error(logPrefix + "TH_OUT frame too short: " + in.length);
            return;
        }

        int count = in[payloadOffset] & 0xFF;
        if (count != 1) {
            LoggerUtil.warn(logPrefix + "TH_OUT unexpected count: " + count);
        }

        String filename = extractNulTerminatedString(in, payloadOffset + 1, 116);
        LoggerUtil.info(logPrefix + "TH_OUT filename: " + filename);

        xferUploadService.handleThResponse(ctx, pacer, state, xferUploadRegistry, filename);
    }
    
    /**
     * Handles TD_OUT token: Client response to our td stat request.
     */
    private void handleTdOutToken(ChannelHandlerContext ctx, byte[] in) {
        LoggerUtil.info(logPrefix + "TD_OUT token received - client returned file stats");

        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "TD_OUT received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.warn(logPrefix + "TD_OUT received but no pending upload");
            return;
        }

        if (state.getPhase() != XferUploadState.Phase.AWAITING_TD_RESPONSE) {
            LoggerUtil.warn(logPrefix + "TD_OUT received but upload in wrong phase: " + state.getPhase());
            return;
        }

        int payloadOffset = 10;
        if (in.length < payloadOffset + 4) {
            LoggerUtil.error(logPrefix + "TD_OUT frame too short: " + in.length);
            return;
        }

        byte rc = in[payloadOffset];
        int size = (in[payloadOffset + 1] & 0xFF) |
                ((in[payloadOffset + 2] & 0xFF) << 8) |
                ((in[payloadOffset + 3] & 0xFF) << 16);

        LoggerUtil.info(logPrefix + String.format("TD_OUT: rc=0x%02X, size=%d bytes", rc & 0xFF, size));

        xferUploadService.handleTdResponse(ctx, pacer, state, xferUploadRegistry, size, rc);
    }
    
    /**
     * Handles xd token: Upload data chunk (escape-encoded).
     */
    private void handleXdToken(ChannelHandlerContext ctx, byte[] in) {
        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "xd received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.debug(logPrefix + "xd received but no active upload - ignoring");
            return;
        }

        int payloadOffset = 10;
        if (in.length <= payloadOffset) {
            LoggerUtil.debug(logPrefix + "xd frame has no data payload");
            return;
        }

        byte[] encodedData = new byte[in.length - payloadOffset];
        System.arraycopy(in, payloadOffset, encodedData, 0, encodedData.length);

        xferUploadService.handleDataChunk(ctx, state, xferUploadRegistry, encodedData);

        // Upload flow control
        if (uploadTnEnabled && state.incrementFrameCountAndCheckTn()) {
            byte[] tnFrame = XferUploadFrameBuilder.buildTnFrame();
            pacer.enqueuePrioritySafe(ctx, tnFrame, "XFER_TN");
            LoggerUtil.info(logPrefix + String.format(
                    "Queued tN after %d xd frames to prompt next burst", state.getReceivedFrameCount()));
        }

        if (uploadAckEnabled && !uploadTnEnabled && state.incrementFrameCountAndCheckAck()) {
            byte[] ack = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, ack, "XFER_PROACTIVE_ACK");
            LoggerUtil.info(logPrefix + String.format(
                    "Sent proactive ACK after %d xd frames received", state.getReceivedFrameCount()));
        } else if (uploadAckEnabled && uploadTnEnabled && (state.getReceivedFrameCount() % 8) == 0) {
            byte[] ack = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, ack, "XFER_PROACTIVE_ACK");
            LoggerUtil.info(logPrefix + String.format(
                    "Sent proactive ACK after %d xd frames received", state.getReceivedFrameCount()));
        }
    }
    
    /**
     * Handles xb token: Upload block boundary (4KB marker with data).
     */
    private void handleXbToken(ChannelHandlerContext ctx, byte[] in) {
        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "xb received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.debug(logPrefix + "xb received but no active upload - ignoring");
            return;
        }

        int payloadOffset = 10;
        if (in.length <= payloadOffset) {
            return;
        }

        byte[] encodedData = new byte[in.length - payloadOffset];
        System.arraycopy(in, payloadOffset, encodedData, 0, encodedData.length);

        xferUploadService.handleBlockMarker(ctx, state, xferUploadRegistry, encodedData);

        // Upload flow control
        if (uploadTnEnabled && state.incrementFrameCountAndCheckTn()) {
            byte[] tnFrame = XferUploadFrameBuilder.buildTnFrame();
            pacer.enqueuePrioritySafe(ctx, tnFrame, "XFER_TN");
            LoggerUtil.info(logPrefix + String.format(
                    "Queued tN after %d xb frames to prompt next burst", state.getReceivedFrameCount()));
        }

        if (uploadAckEnabled && !uploadTnEnabled && state.incrementFrameCountAndCheckAck()) {
            byte[] ack = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, ack, "XFER_PROACTIVE_ACK");
            LoggerUtil.info(logPrefix + String.format(
                    "Sent proactive ACK after %d xb frames received", state.getReceivedFrameCount()));
        } else if (uploadAckEnabled && uploadTnEnabled && (state.getReceivedFrameCount() % 8) == 0) {
            byte[] ack = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, ack, "XFER_PROACTIVE_ACK");
            LoggerUtil.info(logPrefix + String.format(
                    "Sent proactive ACK after %d xb frames received", state.getReceivedFrameCount()));
        }
    }
    
    /**
     * Handles xe token: End of file (final data chunk).
     */
    private void handleXeToken(ChannelHandlerContext ctx, byte[] in) {
        LoggerUtil.info(logPrefix + "xe token received - end of file");

        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "xe received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.warn(logPrefix + "xe received but no active upload");
            return;
        }

        int payloadOffset = 10;
        byte[] encodedData = null;
        if (in.length > payloadOffset) {
            encodedData = new byte[in.length - payloadOffset];
            System.arraycopy(in, payloadOffset, encodedData, 0, encodedData.length);
        }

        xferUploadService.handleEndOfFile(ctx, pacer, state, xferUploadRegistry, encodedData);
    }
    
    /**
     * Handles xK token: Client abort (user cancelled or read error).
     */
    private void handleXKToken(ChannelHandlerContext ctx, byte[] in) {
        LoggerUtil.warn(logPrefix + "xK token received - client aborted upload");

        if (xferUploadRegistry == null || xferUploadService == null) {
            LoggerUtil.error(logPrefix + "xK received but upload components not initialized");
            return;
        }

        XferUploadState state = xferUploadRegistry.getActiveUpload();
        if (state == null) {
            LoggerUtil.warn(logPrefix + "xK received but no active upload");
            return;
        }

        int payloadOffset = 10;
        byte reasonCode = 0x00;
        if (in.length > payloadOffset) {
            reasonCode = in[payloadOffset];
        }

        xferUploadService.handleAbort(ctx, pacer, state, xferUploadRegistry, reasonCode);
    }
    
    /**
     * Extract a NUL-terminated string from a byte array.
     */
    private String extractNulTerminatedString(byte[] data, int offset, int maxLen) {
        if (data == null || offset >= data.length) {
            return "";
        }
        int endOffset = Math.min(offset + maxLen, data.length);
        int len = 0;
        for (int i = offset; i < endOffset; i++) {
            if (data[i] == 0) {
                break;
            }
            len++;
        }
        // Use ISO-8859-1 to preserve all byte values (Mac OS Roman, Windows-1252, etc.)
        return new String(data, offset, len, StandardCharsets.ISO_8859_1);
    }
}
