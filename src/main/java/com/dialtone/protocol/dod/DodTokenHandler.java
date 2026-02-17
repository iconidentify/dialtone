/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.dod;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.protocol.GidUtils;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.P3FrameExtractor;
import com.dialtone.protocol.P3FrameMetadata;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.ControlFrameBuilder;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles DOD (Download on Demand) tokens: f2, f1, K1.
 * Manages GID extraction, DOD request processing, and response handling.
 */
public class DodTokenHandler implements TokenHandler {
    private static final int MAX_BURST_FRAMES = 10;

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final DodRequestHandler dodRequestHandler;
    private final ControlFrameBuilder controlFrameBuilder;
    private final String logPrefix;

    public DodTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                          FdoProcessor fdoProcessor, DodRequestHandler dodRequestHandler,
                          ControlFrameBuilder controlFrameBuilder) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fdoProcessor = fdoProcessor;
        this.dodRequestHandler = dodRequestHandler;
        this.controlFrameBuilder = controlFrameBuilder;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "f2".equals(token) || "f1".equals(token) || "K1".equals(token) || "fh".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        // fh token uses a different processing path (form-based DOD request)
        if ("fh".equals(token)) {
            handleFhToken(ctx, frame);
            return;
        }

        DODTokenBehavior behavior = switch (token) {
            case "f2" -> DODTokenBehavior.F2;
            case "f1" -> DODTokenBehavior.F1;
            case "K1" -> DODTokenBehavior.K1;
            default -> null;
        };

        if (behavior != null) {
            handleDodToken(ctx, frame, behavior);
        }
    }

    /**
     * Extract token from frame (simplified - assumes standard frame format).
     */
    private String extractToken(byte[] frame) {
        if (frame.length < 10) {
            return null;
        }
        return new String(new byte[]{frame[8], frame[9]}, StandardCharsets.US_ASCII);
    }

    /**
     * Handle fh token: Form-based DOD request.
     * The fh token contains FDO payload with form IDs and transaction IDs,
     * unlike f2/f1/K1 which are direct GID-based requests.
     */
    private void handleFhToken(ChannelHandlerContext ctx, byte[] in) {
        try {
            DodRequestHandler.DodResponse dodResponse = dodRequestHandler.processDodRequest(ctx, in,
                    session.getDisplayName(), session.getPlatform());

            if (dodResponse != null && dodResponse.responseChunks != null) {
                if (dodResponse.responseChunks.isEmpty()) {
                    LoggerUtil.info(logPrefix + String.format(
                            "DOD stream control (no data): streamId=0x%04X - sending control ACK",
                            dodResponse.streamId));
                    controlFrameBuilder.sendErrorAck(ctx, "DOD_CTRL_ACK");
                } else {
                    LoggerUtil.debug(logPrefix + String.format(
                            "Processing DOD request with Stream ID: 0x%04X (%d)",
                            dodResponse.streamId, dodResponse.streamId));

                    // Enqueue DOD response chunks
                    P3ChunkEnqueuer.enqueueChunksWithMixedStreamIds(ctx, pacer, dodResponse.responseChunks,
                            "DOD_RESPONSE", MAX_BURST_FRAMES, session.getDisplayName());
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Failed to process fh DOD request: " + e.getMessage());
            // Send error ACK to prevent client hang
            controlFrameBuilder.sendErrorAck(ctx, "FH_DOD_ERROR");
        }
    }

    /**
     * Unified dispatcher for DOD token handlers (f2, f1, K1).
     * Extracts GID, calls appropriate handler, and manages response/error flow.
     *
     * @param ctx      Channel context
     * @param in       Raw frame data
     * @param behavior Token behavior configuration
     */
    private void handleDodToken(ChannelHandlerContext ctx, byte[] in, DODTokenBehavior behavior) {
        int streamId = 0;
        try {
            // Step 1: Extract Stream ID from P3 frame header
            P3FrameMetadata metadata = P3FrameExtractor.extractMetadata(in);
            streamId = metadata.getStreamId();

            // Step 2: Extract GID based on behavior
            int gid;
            int responseId = 0; // Only used for K1
            String gidDisplay;

            if (behavior.gidExtraction() == DODTokenBehavior.GidExtractionMethod.FDO_DECOMPILE) {
                // K1-style: use native FdoStream extraction (no HTTP decompilation needed)
                FdoStreamExtractor.K1Parameters k1Params = FdoStreamExtractor.extractK1Parameters(in);
                gid = k1Params.gid;
                responseId = k1Params.responseId;
                gidDisplay = GidUtils.formatToDisplay(gid);
            } else {
                // f1/f2-style: binary extraction
                DODGidConfig config = behavior.tokenName().equals("f2") ? DODGidConfig.F2 : DODGidConfig.F1;
                int tokenOffset = findTokenOffset(in, config.tokenByte1(), config.tokenByte2());
                if (tokenOffset == -1 || tokenOffset + config.minRequiredBytes() > in.length) {
                    LoggerUtil.warn(logPrefix + behavior.tokenName() + " token not found or incomplete GID");
                    sendDodErrorResponse(ctx, behavior, streamId, behavior.tokenName() + "_ERROR");
                    return;
                }
                gid = GidUtils.bytesToGid(in, tokenOffset + config.gidOffset());
                gidDisplay = GidUtils.formatToDisplay(gid);
            }

            // Step 3: Log the request
            if (behavior.tokenName().equals("K1")) {
                LoggerUtil.info(logPrefix + String.format("K1 request: streamId=0x%04X(%d), gid=%s (%d/0x%08X), responseId=%d",
                        streamId, streamId, gidDisplay, gid, gid, responseId));
            } else {
                LoggerUtil.info(logPrefix + String.format("%s DOD request for GID: %s (%d/0x%08X), streamId: 0x%04X",
                        behavior.tokenName(), gidDisplay, gid, gid, streamId));
            }

            // Step 4: Call appropriate handler and get response
            Object response = invokeDodHandler(ctx, behavior, gid, responseId, streamId);

            // Step 5: Handle response
            handleDodResponse(ctx, behavior, response, gidDisplay, streamId);

        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Failed to process " + behavior.tokenName() + " request: " + e.getMessage());
            sendDodErrorResponse(ctx, behavior, streamId, behavior.tokenName() + "_EXCEPTION");
        }
    }

    /**
     * Invoke the appropriate DodRequestHandler method based on token behavior.
     */
    private Object invokeDodHandler(ChannelHandlerContext ctx, DODTokenBehavior behavior,
                                    int gid, int responseId, int streamId) throws Exception {
        return switch (behavior.tokenName()) {
            case "f2" -> dodRequestHandler.processDodRequest(
                    ctx, gid, streamId, session.getDisplayName(), session.getPlatform());
            case "f1" -> dodRequestHandler.processAtomStreamRequest(
                    ctx, gid, streamId, session.getDisplayName(), session.getPlatform());
            case "K1" -> dodRequestHandler.processK1Request(
                    ctx, gid, responseId, streamId, session.getDisplayName(), session.getPlatform());
            default -> throw new IllegalArgumentException("Unknown DOD token: " + behavior.tokenName());
        };
    }

    /**
     * Handle DOD response, either enqueueing chunks or sending error/empty responses.
     */
    private void handleDodResponse(ChannelHandlerContext ctx, DODTokenBehavior behavior,
                                   Object response, String gidDisplay, int streamId) {
        if (response == null) {
            LoggerUtil.warn(logPrefix + String.format("No %s response for GID: %s", behavior.tokenName(), gidDisplay));
            sendDodErrorResponse(ctx, behavior, streamId, behavior.tokenName() + "_NO_RESPONSE");
            return;
        }

        // Handle based on response type
        List<FdoChunk> chunks = null;
        boolean found = true;

        if (response instanceof DodRequestHandler.DodResponse dodResponse) {
            chunks = dodResponse.responseChunks;
            // f2 doesn't have found flag - check if chunks are empty
            found = chunks != null && !chunks.isEmpty();
        } else if (response instanceof DodRequestHandler.AtomStreamResponse atomResponse) {
            chunks = atomResponse.responseChunks;
            found = atomResponse.found;
        } else if (response instanceof DodRequestHandler.K1Response k1Response) {
            chunks = k1Response.responseChunks;
            found = k1Response.found;
        }

        if (chunks == null) {
            LoggerUtil.warn(logPrefix + String.format("No %s response for GID: %s", behavior.tokenName(), gidDisplay));
            sendDodErrorResponse(ctx, behavior, streamId, behavior.tokenName() + "_NO_RESPONSE");
            return;
        }

        if (!found || chunks.isEmpty()) {
            LoggerUtil.info(logPrefix + String.format("%s not found: %s - sending %s on streamId=0x%04X",
                    behavior.tokenName(), gidDisplay, behavior.emptyLogLabel(), streamId));
            sendDodErrorResponse(ctx, behavior, streamId, behavior.emptyLogLabel());
            return;
        }

        LoggerUtil.debug(logPrefix + String.format("Sending %s response: %s (%d chunks)",
                behavior.tokenName(), gidDisplay, chunks.size()));

        P3ChunkEnqueuer.enqueueChunksWithMixedStreamIds(ctx, pacer, chunks,
                behavior.successLogLabel(), MAX_BURST_FRAMES, session.getDisplayName());
    }

    /**
     * Send error response based on token behavior configuration.
     */
    private void sendDodErrorResponse(ChannelHandlerContext ctx, DODTokenBehavior behavior,
                                      int streamId, String reason) {
        if (behavior.errorResponse() == DODTokenBehavior.ErrorResponseType.SEND_ACK) {
            controlFrameBuilder.sendErrorAck(ctx, reason);
        } else {
            try {
                fdoProcessor.compileAndSend(ctx, behavior.errorBuilder(),
                        session, "AT", streamId, reason);
            } catch (Exception e) {
                LoggerUtil.error(logPrefix + "Failed to send " + behavior.tokenName() + " error response: " + e.getMessage());
                // Fallback to ACK if template fails
                controlFrameBuilder.sendErrorAck(ctx, reason);
            }
        }
    }

    /**
     * Find the offset of a two-byte token in the packet payload.
     * Tokens typically appear in the payload after the P3 header.
     *
     * @param packet     the complete P3 frame
     * @param tokenByte1 first byte of token (e.g., 0x66 for 'f')
     * @param tokenByte2 second byte of token (e.g., 0x31 for '1' or 0x32 for '2')
     * @return offset of token, or -1 if not found
     */
    private int findTokenOffset(byte[] packet, byte tokenByte1, byte tokenByte2) {
        // Token typically appears in the payload after the P3 header
        // Search starting from byte 8 (after standard header)
        for (int i = 8; i <= packet.length - 6; i++) {
            if (packet[i] == tokenByte1 && packet[i + 1] == tokenByte2) {
                return i;
            }
        }
        return -1;
    }
}
