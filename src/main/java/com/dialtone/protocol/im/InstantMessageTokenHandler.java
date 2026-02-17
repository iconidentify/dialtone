/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.im;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.AckIsFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.fdo.dsl.builders.ReceiveImFdoBuilder;
import com.dialtone.fdo.dsl.builders.SendImEchoFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.MultiFrameStreamProcessor;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.UnifiedStreamReassembler;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.auth.UserRegistry;
import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.ChatContext;
import com.dialtone.chat.bot.GrokBot;
import com.dialtone.chat.bot.VirtualUser;
import com.dialtone.web.services.ScreennamePreferencesService;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles instant message tokens: iS (with ACK), iT (no ACK, with echo).
 * Manages multi-frame IM accumulation, delivery, and echo.
 */
public class InstantMessageTokenHandler implements TokenHandler {
    private static final int MAX_BURST_FRAMES = 10;

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final UserRegistry userRegistry;
    private final ScreennamePreferencesService preferencesService;
    private final Properties properties;
    private final UnifiedStreamReassembler unifiedStreamReassembler;
    private final String logPrefix;

    // Multi-frame iS accumulation: Keyed by Stream ID to track incomplete instant message streams.
    private final Map<Integer, List<byte[]>> pendingIsStreams = new ConcurrentHashMap<>();

    public InstantMessageTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                                      UserRegistry userRegistry, ScreennamePreferencesService preferencesService,
                                      Properties properties) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.userRegistry = userRegistry;
        this.preferencesService = preferencesService;
        this.properties = properties;
        this.unifiedStreamReassembler = new UnifiedStreamReassembler();
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "iS".equals(token) || "iT".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        IMTokenBehavior behavior = "iS".equals(token) ? IMTokenBehavior.IS : IMTokenBehavior.IT;
        dispatchInstantMessageToken(ctx, frame, behavior);
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
     * Unified token dispatch for iS and iT instant message tokens.
     */
    private void dispatchInstantMessageToken(ChannelHandlerContext ctx, byte[] in, IMTokenBehavior behavior) {
        try {
            // Extract Stream ID from frame header (bytes 10-11)
            int streamId = MultiFrameStreamProcessor.extractStreamId(in);

            // Log frame arrival with size and hex preview
            LoggerUtil.info(logPrefix + String.format(
                    "%s frame RECEIVED: streamId=0x%04X | size=%d bytes | hex=[%s]",
                    behavior.tokenName(), streamId, in.length,
                    bytesToHex(Arrays.copyOfRange(in, 0, Math.min(in.length, 50)))
            ));

            // Check if this frame has uni_end_stream (signals completion)
            if (MultiFrameStreamProcessor.isUniEndStream(in, fdoCompiler)) {
                LoggerUtil.debug(() -> logPrefix + String.format(
                        "%s with uni_end_stream for Stream ID 0x%04X", behavior.tokenName(), streamId));

                // Check if we have accumulated data for this stream
                List<byte[]> accumulatedFrames = pendingIsStreams.get(streamId);

                if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                    // Multi-frame: combine ALL frames (accumulated + final)
                    List<byte[]> allFrames = new ArrayList<>(accumulatedFrames);
                    allFrames.add(in);

                    LoggerUtil.info(logPrefix + String.format(
                            "Processing MULTI-FRAME %s: %d accumulated + 1 final = %d TOTAL frames | streamId=0x%04X",
                            behavior.tokenName(), accumulatedFrames.size(), allFrames.size(), streamId
                    ));

                    // Process multi-frame instant message
                    if (behavior == IMTokenBehavior.IS) {
                        handleMultiFrameInstantMessage(ctx, allFrames, streamId);
                    } else {
                        handleMultiFrameInstantMessageNoAck(ctx, allFrames, streamId);
                    }

                    // Clear accumulation for this stream
                    pendingIsStreams.remove(streamId);
                } else {
                    // Single-frame: use as-is
                    LoggerUtil.debug(() -> logPrefix + String.format(
                            "Single-frame %s (has uni_end_stream)", behavior.tokenName()));

                    if (behavior == IMTokenBehavior.IS) {
                        handleInstantMessage(ctx, in, streamId);
                    } else {
                        handleInstantMessageNoAck(ctx, in, streamId);
                    }
                }
            } else {
                // No uni_end_stream: accumulate for multi-frame sequence
                LoggerUtil.info(logPrefix + String.format(
                        "%s fragment ACCUMULATING: streamId=0x%04X | frameSize=%d | hex=[%s]",
                        behavior.tokenName(), streamId, in.length,
                        bytesToHex(Arrays.copyOfRange(in, 0, Math.min(in.length, 50)))
                ));

                pendingIsStreams.computeIfAbsent(streamId, k -> new ArrayList<>())
                        .add(Arrays.copyOf(in, in.length));

                int frameCount = pendingIsStreams.get(streamId).size();
                LoggerUtil.info(logPrefix + String.format(
                        "%s frames ACCUMULATED: streamId=0x%04X now has %d frame(s)",
                        behavior.tokenName(), streamId, frameCount
                ));
            }
        } catch (Exception e) {
            // Enhanced exception logging with full stack trace
            LoggerUtil.error(logPrefix + String.format(
                    "EXCEPTION in %s processing | type=%s | message=%s",
                    behavior.tokenName(), e.getClass().getSimpleName(), e.getMessage()));
            LoggerUtil.error(logPrefix + "Stack trace:");
            for (StackTraceElement element : e.getStackTrace()) {
                LoggerUtil.error(logPrefix + "  at " + element.toString());
            }
            if (e.getCause() != null) {
                LoggerUtil.error(logPrefix + "Caused by: " + e.getCause().toString());
            }
            // Don't rethrow - let protocol continue
        }
    }

    private void handleInstantMessage(ChannelHandlerContext ctx, byte[] isFrame, int streamId) {
        try {
            InstantMessage im = extractInstantMessage(isFrame);
            processInstantMessage(ctx, im, streamId, IMTokenBehavior.IS);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed iS IM processing: " + ex.getMessage());
        }
    }

    private void handleMultiFrameInstantMessage(ChannelHandlerContext ctx, List<byte[]> allFrames, int streamId) {
        try {
            InstantMessage im = extractMultiFrameInstantMessage(allFrames);
            processInstantMessage(ctx, im, streamId, IMTokenBehavior.IS);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed multi-frame iS IM processing: " + ex.getMessage());
        }
    }

    private void handleInstantMessageNoAck(ChannelHandlerContext ctx, byte[] isFrame, int streamId) {
        try {
            InstantMessage im = extractInstantMessage(isFrame);
            processInstantMessage(ctx, im, streamId, IMTokenBehavior.IT);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed iT IM processing: " + ex.getMessage());
        }
    }

    private void handleMultiFrameInstantMessageNoAck(ChannelHandlerContext ctx, List<byte[]> allFrames, int streamId) {
        try {
            InstantMessage im = extractMultiFrameInstantMessage(allFrames);
            processInstantMessage(ctx, im, streamId, IMTokenBehavior.IT);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed multi-frame iT IM processing: " + ex.getMessage());
        }
    }

    /**
     * Unified instant message processor - handles both iS and iT tokens.
     */
    private void processInstantMessage(ChannelHandlerContext ctx, InstantMessage im,
                                       int streamId, IMTokenBehavior behavior) {
        String sender = session.getUsername();
        String recipient = im.recipient();
        String message = im.message();
        Integer responseId = im.responseId();

        // Handle reply messages (no recipient in payload - lookup using response ID)
        recipient = resolveRecipientForReply(sender, recipient, responseId);
        if (recipient == null) {
            return; // Already logged in resolveRecipientForReply
        }

        LoggerUtil.info(logPrefix + String.format(
                "Sending %s response with Stream ID 0x%04X (matching request)",
                behavior.tokenName(), streamId));

        try {
            // Generate response FDO using DSL builder based on token type
            String fdoSource;
            if (behavior == IMTokenBehavior.IS) {
                // iS: Send ACK with response ID
                AckIsFdoBuilder ackBuilder = new AckIsFdoBuilder(responseId != null ? responseId : 0);
                fdoSource = ackBuilder.toSource(RenderingContext.DEFAULT);
            } else {
                // iT: Send noop
                fdoSource = NoopFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            }

            // Compile and send - CRITICAL: use incoming Stream ID to match request
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "at", streamId);

            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, behavior.logLabel(),
                    MAX_BURST_FRAMES, session.getDisplayName());

            // Deliver IM to recipient
            deliverInstantMessage(sender, recipient, message, responseId);

            // Echo only for iT tokens - sender sees their own message
            if (behavior.echoToSender()) {
                echoInstantMessageToSender(sender, recipient, message, responseId);
            }
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send " + behavior.tokenName() +
                    " response: " + ex.getMessage());
        }
    }

    /**
     * Resolve recipient for reply messages where recipient is not in the payload.
     */
    private String resolveRecipientForReply(String sender, String recipient, Integer responseId) {
        if (recipient != null) {
            return recipient;
        }

        if (responseId == null) {
            LoggerUtil.warn(logPrefix + "Reply message has no recipient and no response ID, dropping");
            return null;
        }

        // Look up the other participant in this conversation
        String resolved = ConversationIdManager.getInstance()
                .getOtherParticipant(responseId, sender);

        if (resolved == null) {
            LoggerUtil.warn(logPrefix + String.format(
                    "Reply detected with response_id 0x%08X but no conversation found, dropping",
                    responseId));
            return null;
        }

        LoggerUtil.debug(logPrefix + String.format(
                "Reply detected: response_id 0x%08X resolves to recipient %s",
                responseId, resolved));
        return resolved;
    }

    /**
     * Extract instant message from single frame.
     */
    private InstantMessage extractInstantMessage(byte[] isFrame) throws Exception {
        // Use native FdoStream extraction (no HTTP decompilation needed)
        return FdoStreamExtractor.extractInstantMessage(isFrame);
    }

    /**
     * Extract instant message from multi-frame sequence using the Large Atom Protocol.
     */
    private InstantMessage extractMultiFrameInstantMessage(List<byte[]> allFrames) throws Exception {
        LoggerUtil.info(logPrefix + String.format("Extracting IM from %d-frame multi-frame sequence using FdoStream", allFrames.size()));
        try {
            // Use native FdoStream extraction - concatenates frames and decodes once
            return FdoStreamExtractor.extractInstantMessage(allFrames);
        } catch (Exception e) {
            // FdoStream extraction failed - return null to match legacy behavior
            LoggerUtil.warn(logPrefix + "FdoStream extraction failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deliver instant message to recipient.
     */
    private void deliverInstantMessage(String sender, String recipient, String message, Integer responseId) {
        // Log delivery attempt with full message details
        LoggerUtil.info(logPrefix + String.format(
                "Delivering IM: from='%s' → to='%s' | responseId=%d | length=%d chars | hex=[%s] | text='%s'",
                sender, recipient, responseId, message.length(),
                bytesToHex(message.getBytes(StandardCharsets.UTF_8)), message
        ));

        // Validate response_id is within 16-bit constraint (required by client protocol)
        if (responseId != null && responseId > 65535) {
            LoggerUtil.warn(logPrefix + String.format(
                    "Protocol violation: response_id %d exceeds 16-bit limit (65535) for IM from %s to %s - client may ignore message!",
                    responseId, sender, recipient));
        }
        if (responseId != null && responseId < 1) {
            LoggerUtil.warn(logPrefix + String.format(
                    "Protocol violation: response_id %d is invalid (must be >= 1) for IM from %s to %s",
                    responseId, sender, recipient));
        }

        // Check if recipient is a bot FIRST - bots are virtual users not in UserRegistry
        if (ChatBotRegistry.getInstance().isBot(recipient)) {
            LoggerUtil.info(String.format("[%s] IM to bot %s: routing to bot handler", sender, recipient));
            deliverIMToBot(sender, recipient, message, responseId);
            return;
        }

        // Check if recipient is online (only for non-bot users)
        if (!userRegistry.isOnline(recipient)) {
            LoggerUtil.debug(String.format("[%s] IM to %s: offline (dropped)", sender, recipient));
            return;
        }

        // Deliver to regular online user
        try {
            UserRegistry.UserConnection recipientConn = userRegistry.getConnection(recipient);
            if (recipientConn == null || !recipientConn.isActive()) {
                LoggerUtil.debug(String.format("[%s] IM to %s: connection not active (dropped)", sender, recipient));
                return;
            }

            // Get or create SYMMETRIC conversation ID (same ID used for both directions)
            ConversationIdManager conversationManager = ConversationIdManager.getInstance();
            int conversationId = conversationManager.getOrCreateConversationId(sender, recipient);

            // Use same conversation ID for window GID to enable per-buddy windows
            int windowId = conversationId;  // One window per conversation/buddy pair

            // Determine recipient's rendering preferences
            boolean lowColorMode = resolveUserLowColorMode(recipient);
            RenderingContext renderCtx = new RenderingContext(ClientPlatform.WINDOWS, lowColorMode);
            ButtonTheme buttonTheme = lowColorMode ? null : ButtonTheme.fromProperties(properties);

            // Build ReceiveImFdoBuilder with configuration
            ReceiveImFdoBuilder receiveBuilder = new ReceiveImFdoBuilder(
                    windowId, sender, message, conversationId, buttonTheme);
            String fdoSource = receiveBuilder.toSource(renderCtx);

            // Single template with client-side branching (man_do_magic_response_id)
            LoggerUtil.debug(logPrefix + String.format("Delivering IM: %s → %s (responseId=%d, client handles window reuse)",
                    sender, recipient, conversationId));

            // Compile the DSL-generated FDO source
            List<FdoChunk> imChunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "AT", -1);

            // Verify chunks were generated
            if (imChunks == null || imChunks.isEmpty()) {
                LoggerUtil.error(String.format("[%s] Failed to compile receive_im FDO - no chunks generated", sender));
                return;
            }

            ChannelHandlerContext recipientCtx = recipientConn.getContext();
            Pacer recipientPacer = recipientConn.getPacer();

            // Check if recipient is in DOD exclusive mode
            if (recipientConn.isDodExclusivityActive()) {
                LoggerUtil.debug(String.format("[%s] IM to %s: deferred (DOD exclusive)", sender, recipient));
                return;
            }

            // Send directly to recipient
            P3ChunkEnqueuer.enqueue(recipientCtx, recipientPacer, imChunks, "RECEIVE_IM", MAX_BURST_FRAMES, recipient);

            // CRITICAL: Drain immediately for async delivery to other users
            recipientPacer.drainLimited(recipientCtx, MAX_BURST_FRAMES);

            LoggerUtil.info(String.format("[%s] IM to %s: delivered", sender, recipient));

        } catch (Exception ex) {
            LoggerUtil.error(String.format("[%s] Failed to deliver IM to %s: %s", sender, recipient, ex.getMessage()));
        }
    }

    /**
     * Echo an instant message back to the sender so they see their own message in the IM window.
     */
    private void echoInstantMessageToSender(String sender, String recipient, String message, Integer responseId) {
        // Log echo attempt
        LoggerUtil.info(logPrefix + String.format(
                "Echoing IM back to sender: from='%s' → sender='%s' | responseId=%d | text='%s'",
                sender, sender, responseId, message
        ));

        // Get sender's connection
        UserRegistry.UserConnection senderConn = userRegistry.getConnection(sender);
        if (senderConn == null || !senderConn.isActive()) {
            LoggerUtil.debug(String.format("[%s] Echo to sender: connection not active (dropped)", sender));
            return;
        }

        // Check if sender is in DOD exclusive mode
        if (senderConn.isDodExclusivityActive()) {
            LoggerUtil.debug(String.format("[%s] Echo to sender: deferred (DOD exclusive)", sender));
            return;
        }

        try {
            // Get the SAME symmetric conversation ID that was used for delivery
            ConversationIdManager conversationManager = ConversationIdManager.getInstance();
            int conversationId = conversationManager.getOrCreateConversationId(sender, recipient);

            // Use same conversation ID for window GID to match where replies appear
            int windowId = conversationId;

            LoggerUtil.debug(logPrefix + String.format(
                    "Echo IM: %s sees their own message (responseId=%d, windowId=%d)",
                    sender, conversationId, windowId));

            // Use DSL builder for IM echo (replaces fdo/send_im_echo_minimal.fdo.txt)
            SendImEchoFdoBuilder echoBuilder = SendImEchoFdoBuilder.echo(windowId, sender, message);
            String echoFdoSource = echoBuilder.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> echoChunks = fdoCompiler.compileFdoScriptToP3Chunks(echoFdoSource, "AT", -1);

            // Verify chunks were generated
            if (echoChunks == null || echoChunks.isEmpty()) {
                LoggerUtil.error(String.format("[%s] Failed to compile echo IM - no chunks generated", sender));
                return;
            }

            ChannelHandlerContext senderCtx = senderConn.getContext();
            Pacer senderPacer = senderConn.getPacer();

            // Send echo to sender
            P3ChunkEnqueuer.enqueue(senderCtx, senderPacer, echoChunks, "ECHO_IM", MAX_BURST_FRAMES, sender);

            // CRITICAL: Drain immediately for async delivery to sender
            senderPacer.drainLimited(senderCtx, MAX_BURST_FRAMES);

            LoggerUtil.info(String.format("[%s] IM echo to sender: delivered", sender));

        } catch (Exception ex) {
            LoggerUtil.error(String.format("[%s] Failed to echo IM to sender: %s", sender, ex.getMessage()));
        }
    }

    /**
     * Deliver an IM to a bot and handle its async response.
     * Bots generate responses asynchronously to avoid blocking the protocol handler.
     * Uses formatted responses to handle message splitting with delays.
     */
    private void deliverIMToBot(String sender, String recipient, String message, Integer responseId) {
        LoggerUtil.info(logPrefix + String.format("Routing IM to bot %s from %s: '%s'",
                recipient, sender, message));

        VirtualUser bot = ChatBotRegistry.getInstance().getBot(recipient);
        if (bot == null || !bot.isActive()) {
            LoggerUtil.warn(logPrefix + "Bot " + recipient + " not found or inactive");
            return;
        }

        // Get/create conversation ID for consistent window tracking
        int conversationId = ConversationIdManager.getInstance()
                .getOrCreateConversationId(sender, recipient);

        // Get bot timeout from properties (default 60 seconds)
        long timeoutMs = Long.parseLong(properties.getProperty("bot.response.timeout.ms", "60000"));

        // Get split delay from properties (default 500ms)
        long splitDelayMs = Long.parseLong(properties.getProperty("formatter.split.delay.ms", "500"));

        // Generate formatted response asynchronously to avoid blocking protocol handler
        CompletableFuture.supplyAsync(() -> {
            try {
                if (bot instanceof GrokBot grokBot) {
                    // Use GrokBot's formatted IM method for message splitting
                    return grokBot.generateFormattedIMResponse(message, sender);
                } else {
                    // Generic bot fallback - wrap single response in list
                    ChatContext ctx = new ChatContext(null, List.of(), 2);
                    String response = bot.generateResponse(message, sender, ctx);
                    return response != null ? List.of(response) : List.<String>of();
                }
            } catch (Exception e) {
                LoggerUtil.error(logPrefix + "Bot response generation failed: " + e.getMessage());
                return List.<String>of();
            }
        })
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .thenAccept(messageParts -> {
            if (messageParts == null || messageParts.isEmpty()) {
                LoggerUtil.warn(logPrefix + "Bot " + recipient + " returned empty response");
                return;
            }

            LoggerUtil.info(logPrefix + String.format("Bot %s generated %d message part(s) for IM",
                    recipient, messageParts.size()));

            // Send each message part with configured delay
            for (int i = 0; i < messageParts.size(); i++) {
                String part = messageParts.get(i);
                if (part == null || part.trim().isEmpty()) {
                    continue;
                }

                if (i == 0) {
                    // Send first message immediately
                    sendBotIMResponse(recipient, sender, part, conversationId);
                } else {
                    // Schedule subsequent messages with delay
                    final String messagePart = part;
                    final int partIndex = i;
                    long cumulativeDelay = splitDelayMs * i;

                    // Use recipient's connection executor for scheduling
                    UserRegistry.UserConnection conn = userRegistry.getConnection(sender);
                    if (conn != null && conn.isActive()) {
                        conn.getContext().executor().schedule(() -> {
                            LoggerUtil.debug(() -> logPrefix + "Sending delayed bot IM part " +
                                    (partIndex + 1) + "/" + messageParts.size());
                            sendBotIMResponse(recipient, sender, messagePart, conversationId);
                        }, cumulativeDelay, TimeUnit.MILLISECONDS);
                    }
                }
            }
        })
        .exceptionally(ex -> {
            if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                LoggerUtil.warn(logPrefix + "Bot " + recipient + " response timed out after " + timeoutMs + "ms");
            } else {
                LoggerUtil.error(logPrefix + "Bot IM response failed: " + ex.getMessage());
            }
            return null;
        });
    }

    /**
     * Send a bot's IM response back to the user.
     * Compiles the receive_im template and delivers it to the user's connection.
     */
    private void sendBotIMResponse(String botUsername, String recipient, String message, int conversationId) {
        LoggerUtil.info(logPrefix + String.format("Sending bot IM response: %s -> %s (convId=%d)",
                botUsername, recipient, conversationId));

        UserRegistry.UserConnection conn = userRegistry.getConnection(recipient);
        if (conn == null || !conn.isActive()) {
            LoggerUtil.debug(logPrefix + "Recipient " + recipient + " not online for bot IM response");
            return;
        }

        // Check if recipient is in DOD exclusive mode
        if (conn.isDodExclusivityActive()) {
            LoggerUtil.debug(logPrefix + "Bot IM to " + recipient + " deferred (DOD exclusive)");
            return;
        }

        try {
            // Use same window ID as conversation ID for per-buddy windows
            int windowId = conversationId;

            // Determine recipient's rendering preferences
            boolean lowColorMode = resolveUserLowColorMode(recipient);
            RenderingContext renderCtx = new RenderingContext(ClientPlatform.WINDOWS, lowColorMode);
            ButtonTheme buttonTheme = lowColorMode ? null : ButtonTheme.fromProperties(properties);

            // Build ReceiveImFdoBuilder with configuration
            ReceiveImFdoBuilder receiveBuilder = new ReceiveImFdoBuilder(
                    windowId, botUsername, message, conversationId, buttonTheme);
            String fdoSource = receiveBuilder.toSource(renderCtx);

            // Compile the DSL-generated FDO source
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "AT", -1);

            if (chunks == null || chunks.isEmpty()) {
                LoggerUtil.error(logPrefix + "Failed to compile bot IM response FDO");
                return;
            }

            ChannelHandlerContext recipientCtx = conn.getContext();
            Pacer recipientPacer = conn.getPacer();

            // Enqueue and drain immediately for async delivery
            P3ChunkEnqueuer.enqueue(recipientCtx, recipientPacer, chunks,
                    "BOT_IM_RESPONSE", MAX_BURST_FRAMES, recipient);
            recipientPacer.drainLimited(recipientCtx, MAX_BURST_FRAMES);

            LoggerUtil.info(logPrefix + "Bot IM response delivered to " + recipient);

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send bot IM response: " + ex.getMessage());
        }
    }

    /**
     * Utility method to convert bytes to hex string for logging.
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "(empty)";
        }

        StringBuilder hex = new StringBuilder();
        int limit = Math.min(bytes.length, 100);

        for (int i = 0; i < limit; i++) {
            if (i > 0) hex.append(" ");
            hex.append(String.format("%02x", bytes[i] & 0xFF));
        }

        if (bytes.length > limit) {
            hex.append(String.format(" ... (%d more bytes)", bytes.length - limit));
        }

        return hex.toString();
    }

    /**
     * Get pending iS streams map (for integration with StatefulClientHandler during migration).
     */
    public Map<Integer, List<byte[]>> getPendingIsStreams() {
        return pendingIsStreams;
    }

    /**
     * Resolve whether a specific user has low color mode enabled.
     *
     * @param username The username to look up
     * @return true if the user has low_color_mode enabled, false otherwise or on error
     */
    private boolean resolveUserLowColorMode(String username) {
        if (preferencesService == null || username == null) {
            return false;
        }
        try {
            ScreennamePreferences prefs = preferencesService.getPreferencesByScreenname(username);
            return prefs.isLowColorModeEnabled();
        } catch (Exception e) {
            LoggerUtil.warn(String.format("[IM] Failed to resolve low color mode for user '%s': %s",
                    username, e.getMessage()));
            return false; // Default to standard theme on error
        }
    }
}
