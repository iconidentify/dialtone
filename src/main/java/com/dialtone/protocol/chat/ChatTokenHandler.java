/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.chat;

import com.dialtone.chat.ChatRoom;
import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.ChatContext;
import com.dialtone.chat.fdo.ChatRoomFdoBuilder;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.MultiFrameStreamProcessor;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.protocol.chat.ChatFrameBuilder;
import com.dialtone.auth.UserRegistry;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles chat-related tokens: Aa (chat message), ME/CJ (chat open), CO (chat open confirmation), CL (chat leave).
 * Manages multi-frame Aa accumulation, chat broadcasts, and bot integration.
 */
public class ChatTokenHandler implements TokenHandler {
    private static final int MAX_BURST_FRAMES = 10;
    private static final long CHAT_OPEN_TIMEOUT_MS = 10000L;  // 10 seconds for CO token after ME

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final UserRegistry userRegistry;
    private final ChatBotRegistry botRegistry;
    private final Properties properties;
    private final String logPrefix;
    private final ChatFrameBuilder chatFrameBuilder;

    // Multi-frame Aa accumulation: Keyed by Stream ID to track incomplete chat message streams.
    private final Map<Integer, List<byte[]>> pendingAaStreams = new ConcurrentHashMap<>();

    // Chat Open (CO) timeout tracking: Maps username to scheduled timeout task.
    private final Map<String, ScheduledFuture<?>> pendingChatOpenTimeouts = new ConcurrentHashMap<>();

    /**
     * Chat event types for notification frames.
     */
    public enum ChatEventType {
        ARRIVAL('A'),    // User joined chat
        DEPARTURE('B');  // User left chat

        private final char code;

        ChatEventType(char code) {
            this.code = code;
        }

        public char getCode() {
            return code;
        }
    }

    public ChatTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                            FdoProcessor fdoProcessor, UserRegistry userRegistry, ChatBotRegistry botRegistry, Properties properties) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fdoProcessor = fdoProcessor;
        this.userRegistry = userRegistry;
        this.botRegistry = botRegistry;
        this.properties = properties;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
        this.chatFrameBuilder = new ChatFrameBuilder(logPrefix);
    }

    @Override
    public boolean canHandle(String token) {
        return "Aa".equals(token) || "ME".equals(token) || "CJ".equals(token) 
                || "CO".equals(token) || "CL".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        switch (token) {
            case "Aa":
                handleAaToken(ctx, frame);
                break;
            case "ME":
            case "CJ":
                handleMeChatNow(ctx, frame);
                break;
            case "CO":
                handleChatOpen(ctx);
                break;
            case "CL":
                handleChatLeave(ctx, frame);
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
     * Handle Aa (chat message) token with multi-frame support.
     */
    public void handleAaToken(ChannelHandlerContext ctx, byte[] in) {
        try {
            // Extract Stream ID from frame header (bytes 10-11)
            int streamId = MultiFrameStreamProcessor.extractStreamId(in);
            LoggerUtil.debug(() -> logPrefix + String.format("Aa frame with Stream ID: 0x%04X", streamId));

            // Check if this frame has uni_end_stream (signals completion)
            if (MultiFrameStreamProcessor.isUniEndStream(in, fdoCompiler)) {
                LoggerUtil.debug(() -> logPrefix + String.format("Aa with uni_end_stream for Stream ID 0x%04X", streamId));

                // Check if we have accumulated data for this stream
                List<byte[]> accumulatedFrames = pendingAaStreams.get(streamId);

                // Log multi-frame status at DEBUG
                if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                    LoggerUtil.debug(() -> logPrefix + String.format("Multi-frame Aa: %d accumulated + 1 final = %d total for Stream 0x%04X",
                            accumulatedFrames.size(), accumulatedFrames.size() + 1, streamId));
                }

                String chatMessage;
                if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                    // Multi-frame: combine ALL frames (accumulated + final) and extract message
                    List<byte[]> allFrames = new ArrayList<>(accumulatedFrames);
                    allFrames.add(in);  // Add final frame with uni_end_stream

                    // Extract message from ALL frames combined using shared utility
                    chatMessage = MultiFrameStreamProcessor.extractDeDataFromMultiFrame(allFrames, fdoCompiler, "Aa");

                    // Clear accumulation for this stream
                    pendingAaStreams.remove(streamId);
                } else {
                    // Single-frame: extract de_data using shared utility
                    LoggerUtil.debug(() -> logPrefix + "Single-frame Aa (has uni_end_stream)");
                    chatMessage = MultiFrameStreamProcessor.extractDeDataFromSingleFrame(in, fdoCompiler, "Aa");
                }

                // Broadcast the message
                String senderUsername = session.getUsername();

                // Get sender's global chat tag for AA echo frame
                int senderTag = userRegistry != null ? userRegistry.getGlobalChatTag(senderUsername) : -1;
                if (senderTag == -1) {
                    LoggerUtil.warn(logPrefix + "Could not get sender's global tag for AA echo - user may not be in chat");
                    return; // Skip echo if tag not available
                }

                // Build AA frame with sender's mat_relative_tag
                // SAME frame goes to both sender (echo) and all recipients (broadcast)
                byte[] chatFrame = buildChatMessageMatRelativeId(chatMessage, senderTag);

                // Broadcast to all users in chat room
                if (userRegistry != null) {
                    List<UserRegistry.UserConnection> connections = userRegistry.getAllConnections();

                    // Queue AA frame to sender's Pacer (echo)
                    pacer.enqueuePrioritySafe(ctx, chatFrame, "AA_ECHO");

                    // Broadcast SAME AA frame to OTHER users
                    broadcastChatMessageAndDrain(chatFrame, "AA_CHAT", connections, senderUsername);

                    // Process message through bot registry for potential bot responses
                    processBotResponses(chatMessage, senderUsername, connections);

                    // Log chat message at INFO level (functional analytics)
                    LoggerUtil.info(String.format("[CHAT] message sent | user:%s | length:%d | recipients:%d",
                            senderUsername, chatMessage.length(), connections.size() - 1));
                } else {
                    // Fallback to echo if UserRegistry not available
                    pacer.enqueuePrioritySafe(ctx, chatFrame, "AA_ECHO");
                    LoggerUtil.debug(() -> logPrefix + "Echo frame queued (no UserRegistry)");
                }
            } else {
                // No uni_end_stream: this is part of multi-frame sequence
                LoggerUtil.debug(() -> logPrefix + String.format("Aa fragment for Stream ID 0x%04X (waiting for uni_end_stream)", streamId));

                // Accumulate this frame for this Stream ID
                pendingAaStreams.computeIfAbsent(streamId, k -> new ArrayList<>()).add(
                        Arrays.copyOf(in, in.length)
                );

                // Log accumulation state at DEBUG
                LoggerUtil.debug(() -> logPrefix + String.format("Accumulated Aa frames: Stream 0x%04X now has %d frame(s)",
                        streamId, pendingAaStreams.get(streamId).size()));
            }
        } catch (Exception e) {
            // Enhanced exception logging with full stack trace and context
            LoggerUtil.error(logPrefix + String.format(
                    "EXCEPTION in Aa processing | type=%s | message=%s",
                    e.getClass().getSimpleName(),
                    e.getMessage()));
            // Log stack trace manually
            LoggerUtil.error(logPrefix + "Stack trace:");
            for (StackTraceElement element : e.getStackTrace()) {
                LoggerUtil.error(logPrefix + "  at " + element.toString());
            }
            if (e.getCause() != null) {
                LoggerUtil.error(logPrefix + "Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            // Don't rethrow - let protocol continue (same as other handlers)
        }
    }

    /**
     * Handle ME/CJ (Chat Now) token - user wants to join chat room.
     */
    public void handleMeChatNow(ChannelHandlerContext ctx, byte[] in) {
        try {
            // Step 1: Build chat room with ALL members INCLUDING joining user
            // NOTE: User is NOT marked as inChat yet - waiting for CO (Chat Open) confirmation
            // The joining user will see themselves in the FDO, other users will get CA later
            ChatRoom chatRoom = new ChatRoom("Dialtone Lobby");
            Map<String, Integer> userTagMap = new HashMap<>();

            // Step 2a: Add all bots to the room
            List<com.dialtone.chat.bot.VirtualUser> bots = botRegistry.getActiveBots();
            for (com.dialtone.chat.bot.VirtualUser bot : bots) {
                chatRoom.addUser(bot.getUsername());
                int botTag = userRegistry.assignGlobalChatTag(bot.getUsername());
                userTagMap.put(bot.getUsername(), botTag);
                LoggerUtil.debug(logPrefix + "Added bot '" + bot.getUsername() + "' to chat room FDO with tag " + botTag);
            }

            // Step 2b: Add all existing human users to the room
            List<UserRegistry.UserConnection> existingMembers = userRegistry.getOrderedChatMembers();
            int existingUserCount = 0;
            for (UserRegistry.UserConnection member : existingMembers) {
                // Get the user's global tag
                int memberTag = userRegistry.getGlobalChatTag(member.getUsername());
                if (memberTag == -1) {
                    LoggerUtil.warn(logPrefix + "Could not get global tag for user '" + member.getUsername() + "' - skipping");
                    continue;
                }
                // Add to room and tag map
                chatRoom.addUser(member.getUsername());
                userTagMap.put(member.getUsername(), memberTag);
                LoggerUtil.debug(logPrefix + "Added user '" + member.getUsername() + "' to chat room FDO with tag " + memberTag);
                existingUserCount++;
            }

            // Step 2c: Add joining user to the room (they will see themselves in the FDO)
            String joiningUsername = session.getUsername();
            int joiningUserTag = userRegistry.assignGlobalChatTag(joiningUsername);
            chatRoom.addUser(joiningUsername);
            userTagMap.put(joiningUsername, joiningUserTag);
            LoggerUtil.debug(logPrefix + "Added joining user '" + joiningUsername + "' to chat room FDO with tag " + joiningUserTag);

            // Step 3: Compile chat room FDO with ALL members including joining user (uses tag map for correct tags)
            ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(chatRoom, session.getDisplayName(), userTagMap);
            String fdoSource = builder.toSource();
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "at", 0x2A);

            // Log P3 compilation stats
            int totalBytes = chunks.stream().mapToInt(c -> c.getBinaryData().length).sum();
            LoggerUtil.debug(logPrefix + "Chat Now P3 compilation: " + chunks.size() + " chunks, " + totalBytes + " bytes total");
            LoggerUtil.info(logPrefix + "P3 Stats: chunkCount=" + chunks.size() + ", totalSize=" + totalBytes + " bytes");

            // Step 4: Enqueue chat room FDO
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "CHAT_NOW", MAX_BURST_FRAMES, session.getDisplayName());

            // Step 5: Drain FDO frames to client
            pacer.drainLimited(ctx, 20);

            // Step 6: Schedule timeout task - if CO doesn't arrive within timeout, log warning
            final String username = session.getUsername();
            ScheduledFuture<?> timeoutTask = ctx.executor().schedule(() -> {
                try {
                    LoggerUtil.warn("[" + username + "] CO (Chat Open) token not received within " +
                            CHAT_OPEN_TIMEOUT_MS + "ms - chat room may have failed to load on client");
                    pendingChatOpenTimeouts.remove(username.toLowerCase());
                } catch (Exception e) {
                    LoggerUtil.error("[" + username + "] Error in CO timeout handler: " + e.getMessage());
                }
            }, CHAT_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Store timeout task so CO handler can cancel it
            pendingChatOpenTimeouts.put(username.toLowerCase(), timeoutTask);

            LoggerUtil.info("fdo source: " + fdoSource);
            LoggerUtil.info(logPrefix + "Sent chat room FDO with " + bots.size() + " bot(s) + " +
                    existingUserCount + " existing user(s) + self - waiting for CO confirmation");

        } catch (com.dialtone.fdo.spi.FdoCompilationException ex) {
            LoggerUtil.error(logPrefix + "Failed Chat Now P3 compilation: " + ex.getMessage());
            throw new RuntimeException("Failed Chat Now P3 compilation", ex);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed Chat Now processing: " + ex.getMessage());
            throw new RuntimeException("Failed Chat Now processing", ex);
        }
    }

    /**
     * Handle CO (Chat Open) token - client confirmation that chat room loaded successfully.
     */
    public void handleChatOpen(ChannelHandlerContext ctx) {
        try {
            String username = session.getUsername();
            LoggerUtil.info(logPrefix + "CO (Chat Open) token received - client confirmed chat room loaded");

            // Step 1: Cancel pending timeout task
            ScheduledFuture<?> timeoutTask = pendingChatOpenTimeouts.remove(username.toLowerCase());
            if (timeoutTask != null) {
                boolean cancelled = timeoutTask.cancel(false);
                LoggerUtil.debug(logPrefix + "Cancelled CO timeout task (was cancelled: " + cancelled + ")");
            } else {
                LoggerUtil.warn(logPrefix + "No pending CO timeout found for user - may have already timed out");
            }

            // Step 2: Mark user as in chat (captures join timestamp and assigns global tag)
            UserRegistry.UserConnection connection = userRegistry.getConnection(username);
            if (connection == null) {
                LoggerUtil.error(logPrefix + "CO received but user not found in registry - cannot complete chat join");
                return;
            }
            connection.setInChat(true);
            LoggerUtil.debug(logPrefix + "User marked as inChat - join timestamp captured");

            // Step 3: Get user's global tag and build CA frame
            int selfGlobalTag = userRegistry.getGlobalChatTag(username);
            if (selfGlobalTag == -1) {
                LoggerUtil.error(logPrefix + "Failed to get global tag for user after setInChat - CA broadcast skipped");
                return;
            }
            byte[] selfCaFrame = buildChatArrivalFrame(username, selfGlobalTag);

            // Step 4: Broadcast CA to all other users in chat (excluding self - they saw themselves in FDO)
            userRegistry.broadcastToUsersInChatExcept(selfCaFrame, "CA_BROADCAST", username);
            LoggerUtil.info(logPrefix + "Broadcast CA (tag=" + selfGlobalTag + ") to other users in chat (excluded self)");

            // Step 5: Notify bots that user joined
            List<com.dialtone.chat.bot.VirtualUser> bots = botRegistry.getActiveBots();
            for (com.dialtone.chat.bot.VirtualUser bot : bots) {
                bot.onJoinChatRoom("Dialtone Lobby");
            }
            LoggerUtil.debug(logPrefix + "Notified " + bots.size() + " bot(s) of user join");

            // Step 6: Send noop response to acknowledge CO token
            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "CO_ACK");

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to process CO (Chat Open): " + ex.getMessage());
        }
    }

    /**
     * Handle CL (Chat Leave) token - user closed their chat window.
     */
    public void handleChatLeave(ChannelHandlerContext ctx, byte[] in) {
        try {
            LoggerUtil.info(logPrefix + "CL (Chat Leave) token received");

            // Delegate to shared departure logic
            boolean departed = processChatDeparture();
            if (!departed) {
                LoggerUtil.warn(logPrefix + "CL received but user not in chat or not authenticated");
            }

            // Send noop response to acknowledge CL token
            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "CL_ACK");

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to process CL (chat leave): " + ex.getMessage());
        }
    }

    /**
     * Process chat departure for the current user.
     * Called from channelInactive to clean up chat state.
     */
    public boolean processChatDeparture() {
        String username = session.getUsername();

        if (username == null) {
            LoggerUtil.debug(logPrefix + "Chat departure skipped - user not authenticated");
            return false;
        }

        // Get user's global tag BEFORE marking them as out of chat (tag gets removed when leaving)
        int userTag = userRegistry.getGlobalChatTag(username);
        if (userTag == -1) {
            LoggerUtil.debug(logPrefix + "Chat departure skipped - user not in chat (no global tag)");
            return false;
        }

        // Update user's inChat status (this will remove their global tag)
        UserRegistry.UserConnection connection = userRegistry.getConnection(username);
        if (connection != null) {
            connection.setInChat(false);

            // Build and broadcast User Exit (CB) notification to other users still in chat
            byte[] cbFrame = buildChatDepartureFrame(username, userTag);
            userRegistry.broadcastToUsersInChat(cbFrame, "USER_EXIT");
            LoggerUtil.info(logPrefix + "User Exit (CB) broadcast sent with tag " + userTag + " to users in chat");
            return true;
        } else {
            LoggerUtil.warn(logPrefix + "Could not update inChat flag - user '" + username + "' not found in registry");
            return false;
        }
    }

    /**
     * Build AA chat message frame using mat_relative_tag to identify sender.
     */
    public byte[] buildChatMessageMatRelativeId(String message, int matRelativeTag) {
        return chatFrameBuilder.buildChatMessageMatRelativeId(message, matRelativeTag);
    }

    /**
     * Build AB chat message frame with explicit username in payload.
     */
    public byte[] buildChatMessageWithUsername(String username, String message) {
        return chatFrameBuilder.buildChatMessageWithUsername(username, message);
    }

    /**
     * Build a chat notification frame (arrival or departure).
     */
    public byte[] buildChatArrivalFrame(String username, int matRelativeTag) {
        return chatFrameBuilder.buildChatNotificationFrame(username, matRelativeTag, ChatEventType.ARRIVAL);
    }

    /**
     * Build a chat notification frame (arrival or departure).
     */
    public byte[] buildChatDepartureFrame(String username, int matRelativeTag) {
        return chatFrameBuilder.buildChatNotificationFrame(username, matRelativeTag, ChatEventType.DEPARTURE);
    }

    /**
     * Broadcast a chat message frame to all users and immediately drain each Pacer.
     */
    public void broadcastChatMessageAndDrain(byte[] frame, String label, List<UserRegistry.UserConnection> connections, String excludeUsername) {
        int successCount = 0;
        int skippedCount = 0;
        int excludedCount = 0;
        int deferredCount = 0;

        String excludeKey = excludeUsername != null ? excludeUsername.toLowerCase() : null;

        for (UserRegistry.UserConnection connection : connections) {
            // Skip the excluded user (typically the sender)
            if (excludeKey != null && connection.getUsername().toLowerCase().equals(excludeKey)) {
                excludedCount++;
                continue;
            }

            // Skip inactive connections
            if (!connection.isActive()) {
                LoggerUtil.warn(logPrefix + "Skipping inactive user during broadcast: " + connection.getUsername());
                skippedCount++;
                continue;
            }

            // Check if user has active DOD transfer - if so, defer the broadcast
            if (connection.isDodExclusivityActive()) {
                connection.queueDeferredBroadcast(frame, label);
                deferredCount++;
                continue;
            }

            // Queue the frame to recipient's Pacer
            connection.getPacer().enqueuePrioritySafe(connection.getContext(), frame, label);

            // CRITICAL: Drain immediately since recipient isn't in their own splitAndDispatch() cycle
            // Without this drain, async messages (bot responses) never get sent
            connection.getPacer().drainLimited(connection.getContext(), MAX_BURST_FRAMES);
            successCount++;
        }

        LoggerUtil.info(logPrefix + "Broadcast '" + label + "' sent to " + successCount + " users" +
                (deferredCount > 0 ? " (deferred for " + deferredCount + " users with active DOD)" : "") +
                (skippedCount > 0 ? " (skipped " + skippedCount + " inactive)" : "") +
                (excludedCount > 0 ? " (excluded " + excludedCount + " sender)" : ""));
    }

    /**
     * Process bot responses for a chat message.
     * Uses formatted responses to handle message splitting with delays between parts.
     */
    public void processBotResponses(String message, String sender, List<UserRegistry.UserConnection> connections) {
        LoggerUtil.info(logPrefix + "processBotResponses CALLED | message='" + message + "' | sender='" + sender + "' | connections=" + connections.size());

        // Create context for bots
        ChatContext context = new ChatContext("Dialtone Lobby", List.of(), connections.size());

        LoggerUtil.info(logPrefix + "Calling ChatBotRegistry.processMessageFormatted() for split-aware responses...");

        // Process message asynchronously with formatted response support
        botRegistry.processMessageFormatted(message, sender, context)
                .whenComplete((responses, error) -> {
                    if (error != null) {
                        LoggerUtil.error(logPrefix + "CompletableFuture completed EXCEPTIONALLY: " + error.getClass().getName() + ": " + error.getMessage());
                        error.printStackTrace();
                    } else {
                        LoggerUtil.info(logPrefix + "CompletableFuture completed SUCCESSFULLY | responses=" + (responses != null ? responses.size() : "NULL"));
                    }
                })
                .thenAccept(responses -> {
                    LoggerUtil.info(logPrefix + "thenAccept CALLBACK INVOKED | responses=" + (responses != null ? responses.size() : "NULL"));

                    if (responses == null) {
                        LoggerUtil.warn(logPrefix + "Bot responses is NULL!");
                        return;
                    }

                    if (responses.isEmpty()) {
                        LoggerUtil.info(logPrefix + "Bot responses is EMPTY (no bots responded)");
                        return;
                    }

                    LoggerUtil.info(logPrefix + "Processing " + responses.size() + " formatted bot response(s)");

                    for (ChatBotRegistry.FormattedBotResponse botResponse : responses) {
                        LoggerUtil.info(logPrefix + "Bot response from '" + botResponse.botUsername() +
                                "' | hasContent=" + botResponse.hasContent() +
                                " | parts=" + botResponse.messageParts().size() +
                                " | delayMs=" + botResponse.delayBetweenMs());

                        if (!botResponse.hasContent()) {
                            continue;
                        }

                        try {
                            // Get bot's mat_relative_tag from UserRegistry
                            int botTag = userRegistry.getGlobalChatTag(botResponse.botUsername());
                            if (botTag == -1) {
                                LoggerUtil.warn(logPrefix + "Could not get bot's global tag - bot may not be in chat");
                                continue; // Skip this bot, try others
                            }

                            // Send each message part with configured delay
                            List<String> parts = botResponse.messageParts();
                            long delayMs = botResponse.delayBetweenMs();
                            String botLabel = "BOT_" + botResponse.botUsername().toUpperCase();

                            for (int i = 0; i < parts.size(); i++) {
                                String part = parts.get(i);
                                if (part == null || part.trim().isEmpty()) {
                                    continue;
                                }

                                if (i == 0) {
                                    // Send first message immediately
                                    sendBotChatMessage(botTag, part, botLabel, connections);
                                } else {
                                    // Schedule subsequent messages with delay
                                    final String messagePart = part;
                                    final int partIndex = i;
                                    long cumulativeDelay = delayMs * i;

                                    // Use any active connection's executor for scheduling
                                    UserRegistry.UserConnection anyConn = connections.stream()
                                            .filter(UserRegistry.UserConnection::isActive)
                                            .findFirst()
                                            .orElse(null);

                                    if (anyConn != null) {
                                        anyConn.getContext().executor().schedule(() -> {
                                            LoggerUtil.debug(() -> logPrefix + "Sending delayed bot message part " +
                                                    (partIndex + 1) + "/" + parts.size());
                                            sendBotChatMessage(botTag, messagePart, botLabel + "_P" + (partIndex + 1), connections);
                                        }, cumulativeDelay, TimeUnit.MILLISECONDS);
                                    }
                                }
                            }

                            LoggerUtil.info(logPrefix + "Bot broadcast scheduled for '" + botResponse.botUsername() +
                                    "' | " + parts.size() + " parts");

                        } catch (Exception ex) {
                            LoggerUtil.error(logPrefix + "Failed to broadcast bot response from '" +
                                    botResponse.botUsername() + "': " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }

                    LoggerUtil.info(logPrefix + "processBotResponses thenAccept completed successfully");
                })
                .exceptionally(throwable -> {
                    LoggerUtil.error(logPrefix + "Bot response processing FAILED in exceptionally handler: " + throwable.getClass().getName() + ": " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });

        LoggerUtil.info(logPrefix + "processBotResponses returning (async future created)");
    }

    /**
     * Send a single bot chat message to all connections.
     * Helper method for processBotResponses to handle both immediate and delayed sends.
     */
    private void sendBotChatMessage(int botTag, String message, String label, List<UserRegistry.UserConnection> connections) {
        try {
            byte[] botFrame = buildChatMessageMatRelativeId(message, botTag);
            LoggerUtil.debug(() -> logPrefix + "Bot AA frame built | tag=" + botTag + " | size=" + botFrame.length + " | msg='" + message + "'");
            broadcastChatMessageAndDrain(botFrame, label, connections, null);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send bot chat message: " + ex.getMessage());
        }
    }

    /**
     * Get pending Aa streams map (for integration with StatefulClientHandler during migration).
     */
    public Map<Integer, List<byte[]>> getPendingAaStreams() {
        return pendingAaStreams;
    }

    /**
     * Get pending chat open timeouts map (for integration with StatefulClientHandler during migration).
     */
    public Map<String, ScheduledFuture<?>> getPendingChatOpenTimeouts() {
        return pendingChatOpenTimeouts;
    }
}
