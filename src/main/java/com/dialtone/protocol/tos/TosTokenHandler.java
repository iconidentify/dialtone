/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.tos;

import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.GrokBot;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.FdoTemplateEngine;
import com.dialtone.fdo.FdoVariableBuilder;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.fdo.dsl.builders.ReceiveImFdoBuilder;
import com.dialtone.fdo.dsl.builders.TosFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.protocol.im.ConversationIdManager;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Handles TOS-related tokens: TO (TOS display), TA (TOS accept), and MOTD.
 */
public class TosTokenHandler implements TokenHandler {
    private final SessionContext session;
    private final Pacer pacer;
    private final FdoProcessor fdoProcessor;
    private final Properties properties;
    private final String logPrefix;

    public TosTokenHandler(SessionContext session, Pacer pacer, FdoProcessor fdoProcessor,
                           Properties properties) {
        this.session = session;
        this.pacer = pacer;
        this.fdoProcessor = fdoProcessor;
        this.properties = properties;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "TO".equals(token) || "TA".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        switch (token) {
            case "TO":
                handleTosDisplay(ctx);
                break;
            case "TA":
                handleTosAccept(ctx);
                break;
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
     * Handle TO (TOS display) token - shows TOS modal dialog to user.
     */
    public void handleTosDisplay(ChannelHandlerContext ctx) {
        try {
            LoggerUtil.info(logPrefix + "Terms of Service display request received");

            // Determine rendering context (platform + color mode)
            boolean lowColorMode = isLowColorModeEnabled();
            RenderingContext renderCtx = new RenderingContext(session.getPlatform(), lowColorMode);

            // Load TOS content from file
            Map<String, String> tosVariables = new FdoVariableBuilder()
                    .withTos()
                    .build();
            String tosContent = tosVariables.getOrDefault("TOS_DATA", "Terms of Service content unavailable.");

            // Build button theme from properties
            ButtonTheme buttonTheme = ButtonTheme.fromProperties(properties);

            // Generate FDO source using DSL builder
            TosFdoBuilder builder = new TosFdoBuilder(tosContent, buttonTheme);
            String fdoSource = builder.toSource(renderCtx);

            // Compile FDO source to P3 chunks
            FdoCompiler compiler = fdoProcessor.getCompiler();
            List<FdoChunk> chunks = compiler.compileFdoScriptToP3Chunks(
                    fdoSource,
                    "AT",
                    0x25  // Stream ID for TOS display
            );

            LoggerUtil.info(String.format(
                    logPrefix + "Compiled TOS FDO via DSL: %d chunks%s",
                    chunks != null ? chunks.size() : 0,
                    lowColorMode ? " (BW mode)" : ""
            ));

            // Send P3 chunks via Pacer
            if (chunks != null && !chunks.isEmpty()) {
                P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "TOS_DISPLAY", 1, session.getDisplayName());
            }
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed TOS FDO compilation: " + ex.getMessage());
            throw new RuntimeException("Failed TOS FDO compilation", ex);
        }
    }

    /**
     * Handle TA (TOS accept) token - user accepted Terms of Service.
     */
    public void handleTosAccept(ChannelHandlerContext ctx) {
        try {
            LoggerUtil.info(logPrefix + "User accepted Terms of Service");
            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "TOS_ACK");

            // Send Grok welcome IM to ephemeral guests after TOS acceptance
            if (session.isEphemeral()) {
                sendGrokWelcomeIM(ctx);
            }
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send TOS accept ACK: " + ex.getMessage());
        }
    }

    /**
     * Handle MOTD (Message of the Day) - displays welcome message.
     */
    public void handleMotd(ChannelHandlerContext ctx) {
        try {
            // MOTD is currently a noop - just acknowledge the request
            // TODO: Implement proper MOTD display when content is available
            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "at", 0x29, "MOTD");
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed MOTD FDO compilation: " + ex.getMessage());
            throw new RuntimeException("Failed MOTD FDO compilation", ex);
        }
    }

    /**
     * Check if low color mode is enabled for this session.
     */
    private boolean isLowColorModeEnabled() {
        // TODO: Implement low color mode detection from session/platform
        return false;
    }

    /**
     * Send a welcome instant message from Grok to ephemeral guest users.
     * This replaces the modal dialog with a conversational welcome that
     * explains the temporary account and allows the user to chat with Grok.
     */
    private void sendGrokWelcomeIM(ChannelHandlerContext ctx) {
        try {
            String guestScreenname = session.getDisplayName();

            // Get the Grok bot from registry
            ChatBotRegistry botRegistry = ChatBotRegistry.getInstance();
            if (botRegistry == null || !botRegistry.isBot("Grok")) {
                LoggerUtil.warn(logPrefix + "Grok bot not available for guest welcome IM");
                return;
            }

            // Compose the welcome message
            String welcomeMessage = String.format(
                "Hey there! Welcome to Dialtone. You're currently using a temporary " +
                "guest account. To get a permanent screenname, visit dialtone.live " +
                "and create an account.");

            // Get or create a conversation ID for this Grok<->guest pair
            int conversationId = ConversationIdManager.getInstance()
                .getOrCreateConversationId("Grok", guestScreenname);

            // Use same window ID as conversation ID for per-buddy windows
            int windowId = conversationId;

            // Build the ReceiveImFdoBuilder (color mode with button theme)
            RenderingContext renderCtx = new RenderingContext(session.getPlatform(), false);
            ButtonTheme buttonTheme = ButtonTheme.fromProperties(properties);
            ReceiveImFdoBuilder receiveBuilder = new ReceiveImFdoBuilder(
                windowId, "Grok", welcomeMessage, conversationId, buttonTheme);
            String fdoSource = receiveBuilder.toSource(renderCtx);

            // Compile the FDO
            FdoCompiler compiler = fdoProcessor.getCompiler();
            List<FdoChunk> chunks = compiler.compileFdoScriptToP3Chunks(fdoSource, "AT", -1);

            if (chunks == null || chunks.isEmpty()) {
                LoggerUtil.error(logPrefix + "Failed to compile Grok welcome IM FDO");
                return;
            }

            // Enqueue and drain
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "GROK_WELCOME_IM", 10, guestScreenname);
            pacer.drainLimited(ctx, 10);

            LoggerUtil.info(logPrefix + "Sent Grok welcome IM to guest: " + guestScreenname);

        } catch (Exception ex) {
            // Log but don't fail - welcome message is optional
            LoggerUtil.error(logPrefix + "Failed to send Grok welcome IM: " + ex.getMessage());
        }
    }
}
