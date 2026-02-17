/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Singleton registry for tracking connected users.
 * Maps username -> (ChannelHandlerContext, Pacer) for message routing.
 * Thread-safe using ConcurrentHashMap.
 */
public class UserRegistry {

    private static final UserRegistry INSTANCE = new UserRegistry();

    // Tag recycling constants
    private static final int MIN_TAG = 2;   // Tag 1 reserved
    private static final int MAX_TAG = 255; // Single-byte protocol limit

    private final ConcurrentHashMap<String, UserConnection> connections;
    private final ConcurrentHashMap<String, Integer> globalChatTags;  // username -> tag
    private final ConcurrentHashMap<Integer, String> tagToUsername;    // tag -> username (reverse lookup)
    private final ConcurrentLinkedQueue<Integer> availableTags;        // Free pool of recycled tags
    private final ConcurrentHashMap<String, Integer> userLastTag;      // username -> last tag used (for reuse)
    private final java.util.concurrent.atomic.AtomicInteger nextGlobalChatTag;

    private UserRegistry() {
        this.connections = new ConcurrentHashMap<>();
        this.globalChatTags = new ConcurrentHashMap<>();
        this.tagToUsername = new ConcurrentHashMap<>();
        this.availableTags = new ConcurrentLinkedQueue<>();
        this.userLastTag = new ConcurrentHashMap<>();
        this.nextGlobalChatTag = new java.util.concurrent.atomic.AtomicInteger(MIN_TAG);
    }

    public static UserRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a user connection with single-session enforcement.
     * Username is stored in lowercase for case-insensitive lookups.
     *
     * <p><b>Single-Session Enforcement:</b><br>
     * If the user is already connected from another location, the old session
     * will be gracefully disconnected with the message "You've been signed on
     * from another location". This ensures only one active session per user.
     *
     * <p><b>Thread Safety:</b><br>
     * Uses atomic {@code compute()} operation to prevent race conditions during
     * check-and-replace. The old session disconnect happens asynchronously to
     * avoid blocking new registration.
     *
     * @param username The username (will be lowercased internally)
     * @param ctx The channel context
     * @param pacer The pacer for this connection
     * @param platform The client platform (Mac, Windows, etc.)
     * @param disconnectHandler Handler for gracefully disconnecting old sessions (null = force close fallback)
     * @return The old UserConnection if one was replaced, null otherwise
     */
    public UserConnection register(String username, ChannelHandlerContext ctx, Pacer pacer,
                                   ClientPlatform platform, SessionDisconnectHandler disconnectHandler) {
        if (username == null || ctx == null || pacer == null) {
            LoggerUtil.warn("Cannot register user with null username, context, or pacer");
            return null;
        }

        String key = username.toLowerCase();
        UserConnection newConnection = new UserConnection(username, ctx, pacer, platform);

        // Capture old connection for return value
        final UserConnection[] capturedOldConnection = {null};

        // Use atomic compute() to handle check-and-replace atomically
        // This prevents race conditions where multiple logins occur simultaneously
        connections.compute(key, (k, existing) -> {
            if (existing != null && existing.isActive()) {
                LoggerUtil.info("User '" + username + "' already connected - will disconnect old session");

                // Capture old connection for return
                capturedOldConnection[0] = existing;

                // Schedule disconnect asynchronously to avoid blocking the atomic operation
                // This ensures the new registration completes quickly
                if (disconnectHandler != null) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        disconnectHandler.disconnectSession(existing,
                            "You've been signed on from another location")
                            .exceptionally(ex -> {
                                LoggerUtil.error("Failed to disconnect old session for user '" +
                                               username + "': " + ex.getMessage());
                                // Fallback: force close if graceful disconnect failed
                                try {
                                    existing.getContext().close();
                                } catch (Exception closeEx) {
                                    LoggerUtil.error("Failed to force close old session: " + closeEx.getMessage());
                                }
                                return null;
                            });
                    });
                } else {
                    // No disconnect handler provided - force close without graceful disconnect
                    LoggerUtil.warn("No disconnect handler provided for user '" + username +
                                  "' - forcing close without graceful disconnect");
                    try {
                        existing.getContext().close();
                    } catch (Exception e) {
                        LoggerUtil.error("Failed to force close old session: " + e.getMessage());
                    }
                }
            }
            return newConnection;
        });

        // Log registration outcome
        if (capturedOldConnection[0] != null) {
            LoggerUtil.info("User '" + username + "' registered (platform: " + platform +
                          ", total online: " + connections.size() + ") [replaced existing session]");
        } else {
            LoggerUtil.info("User '" + username + "' registered (platform: " + platform +
                          ", total online: " + connections.size() + ")");
        }

        return capturedOldConnection[0];
    }

    /**
     * Unregister a user connection.
     * Username lookup is case-insensitive.
     *
     * @param username The username to unregister
     * @return true if user was removed, false if not found
     */
    public boolean unregister(String username) {
        if (username == null) {
            return false;
        }

        String key = username.toLowerCase();
        UserConnection removed = connections.remove(key);

        if (removed != null) {
            LoggerUtil.info("User '" + username + "' unregistered (total online: " + connections.size() + ")");
            return true;
        }

        return false;
    }

    /**
     * Check if a user is currently connected.
     * Case-insensitive lookup.
     *
     * @param username The username to check
     * @return true if user is online, false otherwise
     */
    public boolean isOnline(String username) {
        if (username == null) {
            return false;
        }
        return connections.containsKey(username.toLowerCase());
    }

    /**
     * Get the UserConnection for a specific user.
     * Case-insensitive lookup.
     *
     * @param username The username to look up
     * @return The UserConnection or null if not found
     */
    public UserConnection getConnection(String username) {
        if (username == null) {
            return null;
        }
        return connections.get(username.toLowerCase());
    }

    /**
     * Get all currently connected users.
     *
     * @return List of UserConnection objects (original username casing preserved)
     */
    public List<UserConnection> getAllConnections() {
        return connections.values().stream()
                .collect(Collectors.toList());
    }

    /**
     * Get all users currently in chat, ordered by join time (oldest first).
     * Used for sending CA tokens in the correct order when new users join.
     *
     * @return List of UserConnection objects in chat, sorted by chatJoinTimestamp (oldest → newest)
     */
    public List<UserConnection> getOrderedChatMembers() {
        return connections.values().stream()
                .filter(UserConnection::isInChat)
                .sorted(Comparator.comparingLong(UserConnection::getChatJoinTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Get list of all online usernames.
     *
     * @return List of usernames (original casing preserved)
     */
    public List<String> getOnlineUsernames() {
        return connections.values().stream()
                .map(UserConnection::getUsername)
                .collect(Collectors.toList());
    }

    /**
     * Get count of connected users.
     *
     * @return Number of users currently online
     */
    public int getOnlineCount() {
        return connections.size();
    }

    /**
     * Clear all connections.
     * Primarily for testing purposes.
     */
    public void clear() {
        int count = connections.size();
        connections.clear();
        LoggerUtil.info("UserRegistry cleared (" + count + " users removed)");
    }

    /**
     * Assign a global chat tag to a user with intelligent recycling.
     * Tags are globally consistent across all clients - every client sees the same tag for the same user.
     * <p>
     * Tag Assignment Strategy (in priority order):
     * 1. Return existing tag if user already has one assigned
     * 2. Reuse user's previous tag if it's available (preferred for consistency)
     * 3. Get tag from free pool (recycled from users who left)
     * 4. Allocate new tag from counter (only if pool is empty)
     * 5. Emergency: Scan for first available tag if counter exceeded byte limit
     * <p>
     * Tags are constrained to 2-255 range (single-byte protocol limit).
     *
     * @param username The username to assign a tag to
     * @return The assigned tag (existing or newly assigned), or -1 if allocation failed
     */
    public int assignGlobalChatTag(String username) {
        if (username == null) {
            LoggerUtil.warn("Cannot assign global chat tag for null username");
            return -1;
        }

        String key = username.toLowerCase();

        // Check if user already has an assigned tag (concurrent join protection)
        Integer existingTag = globalChatTags.get(key);
        if (existingTag != null) {
            LoggerUtil.debug("User '" + username + "' already has tag " + existingTag);
            return existingTag;
        }

        // Try to reuse user's previous tag if available (best for consistency)
        Integer lastTag = userLastTag.get(key);
        if (lastTag != null && !tagToUsername.containsKey(lastTag)) {
            // Last tag is free, reclaim it atomically
            Integer previous = globalChatTags.putIfAbsent(key, lastTag);
            if (previous == null) {
                // Successfully claimed the tag
                tagToUsername.put(lastTag, key);
                availableTags.remove(lastTag);  // Remove from free pool if present
                LoggerUtil.info("Reused tag " + lastTag + " for returning user '" + username + "'");
                return lastTag;
            } else {
                // Race condition - another thread assigned a tag first
                LoggerUtil.debug("Race condition during tag reuse for '" + username + "' - using assigned tag " + previous);
                return previous;
            }
        }

        // Try to get tag from free pool (recycled tags)
        Integer freeTag = availableTags.poll();
        if (freeTag != null) {
            Integer previous = globalChatTags.putIfAbsent(key, freeTag);
            if (previous == null) {
                // Successfully claimed the recycled tag
                tagToUsername.put(freeTag, key);
                userLastTag.put(key, freeTag);
                LoggerUtil.info("Assigned recycled tag " + freeTag + " to user '" + username + "'");
                return freeTag;
            } else {
                // Race condition - return tag to pool and use the assigned one
                availableTags.offer(freeTag);
                LoggerUtil.debug("Race condition during recycled tag assignment for '" + username + "' - using assigned tag " + previous);
                return previous;
            }
        }

        // No free tags - allocate new tag from counter
        int tag = nextGlobalChatTag.getAndIncrement();

        // Check if we exceeded single-byte limit
        if (tag > MAX_TAG) {
            LoggerUtil.error("CRITICAL: Tag counter exceeded byte limit! tag=" + tag + " for user '" + username + "'");
            // Emergency fallback: find first available tag in valid range
            tag = findFirstAvailableTag();
            if (tag == -1) {
                LoggerUtil.error("FATAL: All tags exhausted! Cannot assign tag to user '" + username + "'");
                return -1;
            }
            LoggerUtil.warn("Emergency: Assigned tag " + tag + " from available range to user '" + username + "'");
        }

        // Assign the new tag
        Integer previous = globalChatTags.putIfAbsent(key, tag);
        if (previous == null) {
            // Successfully assigned new tag
            tagToUsername.put(tag, key);
            userLastTag.put(key, tag);
            LoggerUtil.info("Assigned new tag " + tag + " to user '" + username + "' (next=" + nextGlobalChatTag.get() + ", pool size=" + availableTags.size() + ")");
            return tag;
        } else {
            // Race condition - another thread assigned first, return our tag to counter
            nextGlobalChatTag.compareAndSet(tag + 1, tag);  // Try to reclaim the wasted tag
            LoggerUtil.debug("Race condition during new tag assignment for '" + username + "' - using assigned tag " + previous);
            return previous;
        }
    }

    /**
     * Emergency fallback: Find first available tag in valid range (MIN_TAG to MAX_TAG).
     * Only called when counter exceeds byte limit.
     *
     * @return First available tag, or -1 if all tags exhausted
     */
    private int findFirstAvailableTag() {
        for (int tag = MIN_TAG; tag <= MAX_TAG; tag++) {
            if (!tagToUsername.containsKey(tag)) {
                return tag;
            }
        }
        return -1;  // All tags exhausted!
    }

    /**
     * Get the global chat tag for a user.
     *
     * @param username The username to look up
     * @return The global tag, or -1 if not assigned
     */
    public int getGlobalChatTag(String username) {
        if (username == null) {
            return -1;
        }
        return globalChatTags.getOrDefault(username.toLowerCase(), -1);
    }

    /**
     * Remove a user's global chat tag when they leave chat.
     * The tag is returned to the free pool for recycling, but the user's last tag is preserved
     * so they can get the same tag back if they rejoin.
     *
     * @param username The username to remove
     * @return true if tag was removed, false if not found
     */
    public boolean removeGlobalChatTag(String username) {
        if (username == null) {
            return false;
        }

        String key = username.toLowerCase();
        Integer removed = globalChatTags.remove(key);

        if (removed != null) {
            // Remove from reverse lookup (tag -> username)
            tagToUsername.remove(removed);

            // Add tag to free pool for recycling
            availableTags.offer(removed);

            // Keep userLastTag entry - user can get same tag back if they return

            LoggerUtil.info("Removed global chat tag " + removed + " for user '" + username +
                          "' (returned to pool, pool size: " + availableTags.size() + ")");
            return true;
        }

        return false;
    }

    /**
     * Clear all global chat tags and reset recycling system.
     * Primarily for testing purposes.
     */
    public void clearAllChatTags() {
        int count = globalChatTags.size();
        globalChatTags.clear();
        tagToUsername.clear();
        availableTags.clear();
        userLastTag.clear();
        nextGlobalChatTag.set(MIN_TAG);
        LoggerUtil.info("All global chat tags cleared (" + count + " tags removed, recycling system reset)");
    }

    /**
     * Broadcast a frame to all users who are currently in chat, except the specified user.
     * This is used for Chat Arrival (CA) notifications to only notify users who have opened chat.
     *
     * <p>Only sends to users where {@code isInChat() == true}. This prevents notifying users
     * who are logged in but haven't opened the chat window yet.
     *
     * <p>Respects DOD exclusivity: if a recipient has an active DOD transfer, the broadcast
     * is deferred until the transfer completes.
     *
     * @param frame The frame bytes to broadcast
     * @param label Debug label for pacer
     */
    public void broadcastToUsersInChat(byte[] frame, String label) {
        if (frame == null || label == null) {
            LoggerUtil.warn("Cannot broadcast: frame or label is null");
            return;
        }

        int broadcastCount = 0;
        int deferredCount = 0;
        int skippedCount = 0;
        int notInChatCount = 0;

        for (UserConnection connection : connections.values()) {

            // Skip inactive connections
            if (!connection.isActive()) {
                LoggerUtil.warn("Skipping inactive user during broadcast: " + connection.getUsername());
                skippedCount++;
                continue;
            }

            // ONLY send to users who are in chat
            if (!connection.isInChat()) {
                notInChatCount++;
                continue;
            }

            // Check if user has active DOD transfer - if so, defer the broadcast
            if (connection.isDodExclusivityActive()) {
                connection.queueDeferredBroadcast(frame, label);
                deferredCount++;
                continue;
            }

            // Enqueue frame and drain immediately
            connection.getPacer().enqueuePrioritySafe(connection.getContext(), frame, label);

            // CRITICAL: Drain immediately since recipient isn't in their own splitAndDispatch() cycle
            // Without this drain, async broadcasts (Chat Arrival, etc.) never get sent
            connection.getPacer().drainLimited(connection.getContext(), 16);
            broadcastCount++;
        }

        LoggerUtil.info("Broadcast '" + label + "' sent to " + broadcastCount + " users in chat" +
                (notInChatCount > 0 ? " (skipped " + notInChatCount + " not in chat)" : "") +
                (deferredCount > 0 ? " (deferred for " + deferredCount + " users with active DOD)" : "") +
                (skippedCount > 0 ? " (skipped " + skippedCount + " inactive)" : ""));
    }

    /**
     * Broadcast a frame to all users who are currently in chat, except the specified user.
     * This is used for Chat Arrival (CA) notifications when we want to notify everyone
     * EXCEPT the user who just joined (they already saw themselves in the chat room FDO).
     *
     * <p>Only sends to users where {@code isInChat() == true}. This prevents notifying users
     * who are logged in but haven't opened the chat window yet.
     *
     * <p>Respects DOD exclusivity: if a recipient has an active DOD transfer, the broadcast
     * is deferred until the transfer completes.
     *
     * @param frame The frame bytes to broadcast
     * @param label Debug label for pacer
     * @param excludeUsername Username to exclude from broadcast (case-insensitive), or null to broadcast to all
     */
    public void broadcastToUsersInChatExcept(byte[] frame, String label, String excludeUsername) {
        if (frame == null || label == null) {
            LoggerUtil.warn("Cannot broadcast: frame or label is null");
            return;
        }

        String excludeKey = excludeUsername != null ? excludeUsername.toLowerCase() : null;
        int broadcastCount = 0;
        int deferredCount = 0;
        int skippedCount = 0;
        int notInChatCount = 0;
        int excludedCount = 0;

        for (UserConnection connection : connections.values()) {

            // Skip inactive connections
            if (!connection.isActive()) {
                LoggerUtil.warn("Skipping inactive user during broadcast: " + connection.getUsername());
                skippedCount++;
                continue;
            }

            // ONLY send to users who are in chat
            if (!connection.isInChat()) {
                notInChatCount++;
                continue;
            }

            // Skip the excluded user
            if (excludeKey != null && connection.getUsername().toLowerCase().equals(excludeKey)) {
                excludedCount++;
                continue;
            }

            // Check if user has active DOD transfer - if so, defer the broadcast
            if (connection.isDodExclusivityActive()) {
                connection.queueDeferredBroadcast(frame, label);
                deferredCount++;
                continue;
            }

            // Enqueue frame and drain immediately
            connection.getPacer().enqueuePrioritySafe(connection.getContext(), frame, label);

            // CRITICAL: Drain immediately since recipient isn't in their own splitAndDispatch() cycle
            // Without this drain, async broadcasts (Chat Arrival, etc.) never get sent
            connection.getPacer().drainLimited(connection.getContext(), 16);
            broadcastCount++;
        }

        LoggerUtil.info("Broadcast '" + label + "' sent to " + broadcastCount + " users in chat" +
                (excludedCount > 0 ? " (excluded: " + excludeUsername + ")" : "") +
                (notInChatCount > 0 ? " (skipped " + notInChatCount + " not in chat)" : "") +
                (deferredCount > 0 ? " (deferred for " + deferredCount + " users with active DOD)" : "") +
                (skippedCount > 0 ? " (skipped " + skippedCount + " inactive)" : ""));
    }

    /**
     * Represents a connected user with their communication channels.
     */
    public static class UserConnection {
        private final String username;  // Original casing preserved
        private final ChannelHandlerContext ctx;
        private final Pacer pacer;
        private final ClientPlatform platform;
        private final ConcurrentLinkedQueue<DeferredBroadcast> deferredBroadcasts;
        private volatile boolean dodExclusivityActive;
        private volatile boolean inChat;
        private volatile long chatJoinTimestamp;  // Timestamp when user joined chat (0 = not in chat)

        public UserConnection(String username, ChannelHandlerContext ctx, Pacer pacer, ClientPlatform platform) {
            this.username = username;
            this.ctx = ctx;
            this.pacer = pacer;
            this.platform = platform != null ? platform : ClientPlatform.UNKNOWN;
            this.deferredBroadcasts = new ConcurrentLinkedQueue<>();
            this.dodExclusivityActive = false;
            this.inChat = false;
            this.chatJoinTimestamp = 0;  // Not in chat initially
        }

        public String getUsername() {
            return username;
        }

        public ChannelHandlerContext getContext() {
            return ctx;
        }

        public Pacer getPacer() {
            return pacer;
        }

        public ClientPlatform getPlatform() {
            return platform;
        }

        /**
         * Check if this connection is still active.
         *
         * @return true if channel is active, false otherwise
         */
        public boolean isActive() {
            return ctx.channel().isActive();
        }

        /**
         * Set whether DOD exclusivity is active for this connection.
         *
         * <p>When active, broadcasts will be deferred until DOD transfer completes.
         *
         * @param active true to activate DOD exclusivity, false to deactivate
         */
        public void setDodExclusivityActive(boolean active) {
            this.dodExclusivityActive = active;
            if (active) {
                LoggerUtil.debug("DOD exclusivity ACTIVATED for user: " + username);
            } else {
                LoggerUtil.debug("DOD exclusivity DEACTIVATED for user: " + username);
            }
        }

        /**
         * Check if DOD exclusivity is active for this connection.
         *
         * @return true if DOD transfer is in progress and broadcasts should be deferred
         */
        public boolean isDodExclusivityActive() {
            return dodExclusivityActive;
        }

        /**
         * Queue a broadcast message to be sent later when DOD completes.
         *
         * @param data The frame bytes to send
         * @param label The pacer label for logging
         */
        public void queueDeferredBroadcast(byte[] data, String label) {
            DeferredBroadcast deferred = new DeferredBroadcast(data, label, System.currentTimeMillis());
            deferredBroadcasts.offer(deferred);
            LoggerUtil.debug("Deferred broadcast '" + label + "' for user " + username +
                           " (queue size: " + deferredBroadcasts.size() + ")");
        }

        /**
         * Check if there are any deferred broadcasts waiting.
         *
         * @return true if queue is not empty
         */
        public boolean hasDeferredBroadcasts() {
            return !deferredBroadcasts.isEmpty();
        }

        /**
         * Get and remove all deferred broadcasts for processing.
         *
         * @return List of deferred broadcasts (empty if none queued)
         */
        public List<DeferredBroadcast> drainDeferredBroadcasts() {
            List<DeferredBroadcast> broadcasts = new ArrayList<>();
            DeferredBroadcast deferred;
            while ((deferred = deferredBroadcasts.poll()) != null) {
                broadcasts.add(deferred);
            }
            return broadcasts;
        }

        /**
         * Get the current size of the deferred broadcast queue.
         *
         * @return Number of deferred broadcasts waiting
         */
        public int getDeferredBroadcastCount() {
            return deferredBroadcasts.size();
        }

        /**
         * Set whether this user is currently in chat.
         * When a user opens the chat window (CJ/ME tokens), this should be set to true.
         * Captures join timestamp using System.nanoTime() for ordering.
         * Automatically manages global chat tag assignment/removal.
         *
         * @param inChat true if user has opened chat, false otherwise
         */
        public void setInChat(boolean inChat) {
            this.inChat = inChat;
            if (inChat) {
                this.chatJoinTimestamp = System.nanoTime();
                // Assign global chat tag when entering chat
                UserRegistry.getInstance().assignGlobalChatTag(username);
                LoggerUtil.debug("User '" + username + "' entered chat (timestamp: " + chatJoinTimestamp + ")");
            } else {
                this.chatJoinTimestamp = 0;  // Reset timestamp when leaving
                // Remove global chat tag when leaving chat
                UserRegistry.getInstance().removeGlobalChatTag(username);
                LoggerUtil.debug("User '" + username + "' left chat");
            }
        }

        /**
         * Check if this user is currently in chat.
         *
         * @return true if user has opened chat window, false otherwise
         */
        public boolean isInChat() {
            return inChat;
        }

        /**
         * Get the timestamp when this user joined chat.
         * Used for preserving join order when sending CA tokens.
         *
         * @return nanosecond timestamp from System.nanoTime(), or 0 if not in chat
         */
        public long getChatJoinTimestamp() {
            return chatJoinTimestamp;
        }
    }
}
