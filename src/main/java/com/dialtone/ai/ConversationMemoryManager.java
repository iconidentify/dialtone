/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.utils.LoggerUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages conversation history for chat bots to maintain context across messages.
 *
 * Features:
 * - Per-user conversation tracking
 * - Configurable message history limit
 * - Automatic cleanup of inactive conversations
 * - Thread-safe for concurrent access
 *
 * Usage:
 *   ConversationMemoryManager memory = new ConversationMemoryManager(10, 30);
 *   memory.addUserMessage("steve", "Hello!");
 *   memory.addBotMessage("steve", "Hi! How can I help?");
 *   List<Message> history = memory.getHistory("steve");
 */
public class ConversationMemoryManager implements AutoCloseable {

    /**
     * Represents a single message in conversation history.
     */
    public static class Message {
        private final String role;  // "user" or "assistant"
        private final String content;
        private final long timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Conversation history for a single user.
     */
    private static class ConversationHistory {
        private final List<Message> messages;
        private long lastAccessTime;
        private final int maxTurns;

        public ConversationHistory(int maxTurns) {
            this.messages = new ArrayList<>();
            this.lastAccessTime = System.currentTimeMillis();
            this.maxTurns = maxTurns;
        }

        public synchronized void addMessage(String role, String content) {
            messages.add(new Message(role, content));
            lastAccessTime = System.currentTimeMillis();

            // Trim to max turns (2 messages per turn: user + assistant)
            int maxMessages = maxTurns * 2;
            while (messages.size() > maxMessages) {
                messages.remove(0);  // Remove oldest message
            }
        }

        public synchronized List<Message> getMessages() {
            lastAccessTime = System.currentTimeMillis();
            return new ArrayList<>(messages);  // Return defensive copy
        }

        public synchronized long getLastAccessTime() {
            return lastAccessTime;
        }

        public synchronized int getMessageCount() {
            return messages.size();
        }

        public synchronized void clear() {
            messages.clear();
            lastAccessTime = System.currentTimeMillis();
        }
    }

    private static final int MAX_CONVERSATIONS = 500;

    private final Map<String, ConversationHistory> conversations;
    private final int maxTurns;
    private final int inactiveTimeoutMinutes;
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * Create a new conversation memory manager.
     *
     * @param maxTurns Maximum number of conversation turns to retain per user (1 turn = user + bot message)
     * @param inactiveTimeoutMinutes Minutes of inactivity before conversation is cleared
     */
    public ConversationMemoryManager(int maxTurns, int inactiveTimeoutMinutes) {
        if (maxTurns < 1) {
            throw new IllegalArgumentException("maxTurns must be at least 1");
        }
        if (inactiveTimeoutMinutes < 1) {
            throw new IllegalArgumentException("inactiveTimeoutMinutes must be at least 1");
        }

        this.conversations = new ConcurrentHashMap<>();
        this.maxTurns = maxTurns;
        this.inactiveTimeoutMinutes = inactiveTimeoutMinutes;

        // Start cleanup task to remove inactive conversations
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConversationMemoryCleanup");
            t.setDaemon(true);
            return t;
        });

        // Run cleanup every 10 minutes
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupInactiveConversations,
                10, 10, TimeUnit.MINUTES
        );

        LoggerUtil.info(String.format("ConversationMemoryManager initialized: maxTurns=%d, timeout=%dm",
                maxTurns, inactiveTimeoutMinutes));
    }

    /**
     * Add a user message to conversation history.
     *
     * @param username The username
     * @param message The message content
     */
    public void addUserMessage(String username, String message) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (message == null) {
            message = "";
        }

        evictIfAtCapacity(username.toLowerCase());

        ConversationHistory history = conversations.computeIfAbsent(
                username.toLowerCase(),
                k -> new ConversationHistory(maxTurns)
        );

        history.addMessage("user", message);
        LoggerUtil.debug(() -> String.format("Added user message for %s (history size: %d)",
                username, history.getMessageCount()));
    }

    /**
     * Add a bot message to conversation history.
     *
     * @param username The username
     * @param message The message content
     */
    public void addBotMessage(String username, String message) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (message == null) {
            message = "";
        }

        evictIfAtCapacity(username.toLowerCase());

        ConversationHistory history = conversations.computeIfAbsent(
                username.toLowerCase(),
                k -> new ConversationHistory(maxTurns)
        );

        history.addMessage("assistant", message);
        LoggerUtil.debug(() -> String.format("Added bot message for %s (history size: %d)",
                username, history.getMessageCount()));
    }

    /**
     * Get conversation history for a user.
     *
     * @param username The username
     * @return List of messages in chronological order (oldest to newest)
     */
    public List<Message> getHistory(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }

        ConversationHistory history = conversations.get(username.toLowerCase());
        if (history == null) {
            return Collections.emptyList();
        }

        return history.getMessages();
    }

    /**
     * Clear conversation history for a specific user.
     *
     * @param username The username
     */
    public void clearHistory(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }

        ConversationHistory history = conversations.get(username.toLowerCase());
        if (history != null) {
            history.clear();
            LoggerUtil.info("Cleared conversation history for " + username);
        }
    }

    /**
     * Clear all conversation history.
     */
    public void clearAllHistory() {
        int count = conversations.size();
        conversations.clear();
        LoggerUtil.info("Cleared all conversation history (" + count + " users)");
    }

    /**
     * Get number of active conversations.
     *
     * @return Number of users with conversation history
     */
    public int getActiveConversationCount() {
        return conversations.size();
    }

    /**
     * Evict the oldest conversation if at capacity and the user isn't already present.
     */
    private void evictIfAtCapacity(String userKey) {
        if (conversations.containsKey(userKey) || conversations.size() < MAX_CONVERSATIONS) {
            return;
        }
        // Find and evict the entry with the oldest lastAccessTime
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, ConversationHistory> entry : conversations.entrySet()) {
            long accessTime = entry.getValue().getLastAccessTime();
            if (accessTime < oldestTime) {
                oldestTime = accessTime;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            conversations.remove(oldestKey);
            LoggerUtil.info("Evicted oldest conversation for " + oldestKey + " (at capacity " + MAX_CONVERSATIONS + ")");
        }
    }

    /**
     * Clean up conversations that have been inactive for too long.
     */
    private void cleanupInactiveConversations() {
        try {
            long now = System.currentTimeMillis();
            long timeoutMs = TimeUnit.MINUTES.toMillis(inactiveTimeoutMinutes);

            List<String> toRemove = new ArrayList<>();

            conversations.forEach((username, history) -> {
                if (now - history.getLastAccessTime() > timeoutMs) {
                    toRemove.add(username);
                }
            });

            for (String username : toRemove) {
                conversations.remove(username);
                LoggerUtil.info("Removed inactive conversation for " + username);
            }

            if (!toRemove.isEmpty()) {
                LoggerUtil.info(String.format("Cleanup: removed %d inactive conversations, %d remain",
                        toRemove.size(), conversations.size()));
            }
        } catch (Exception e) {
            LoggerUtil.error("Error during conversation cleanup: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        LoggerUtil.info("Shutting down ConversationMemoryManager");
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
