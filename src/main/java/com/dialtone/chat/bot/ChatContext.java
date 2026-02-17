/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.bot;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable context provided to bots when generating responses.
 * Contains chat history, sender information, and room context.
 */
public class ChatContext {

    private final String roomName;
    private final List<ChatMessage> recentMessages;
    private final int totalParticipants;

    public ChatContext(String roomName, List<ChatMessage> recentMessages, int totalParticipants) {
        this.roomName = roomName;
        this.recentMessages = recentMessages != null ? List.copyOf(recentMessages) : Collections.emptyList();
        this.totalParticipants = totalParticipants;
    }

    public String getRoomName() {
        return roomName;
    }

    public List<ChatMessage> getRecentMessages() {
        return recentMessages;
    }

    public int getTotalParticipants() {
        return totalParticipants;
    }

    /**
     * Represents a single chat message in the history.
     */
    public static class ChatMessage {
        private final String sender;
        private final String content;
        private final Instant timestamp;
        private final MessageSource source;

        public ChatMessage(String sender, String content, Instant timestamp, MessageSource source) {
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
            this.source = source;
        }

        public String getSender() {
            return sender;
        }

        public String getContent() {
            return content;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public MessageSource getSource() {
            return source;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", source, sender, content);
        }
    }

    /**
     * Indicates whether a message came from a human or a bot.
     */
    public enum MessageSource {
        HUMAN,
        BOT
    }

    @Override
    public String toString() {
        return String.format("ChatContext{room='%s', participants=%d, messages=%d}",
                roomName, totalParticipants, recentMessages.size());
    }
}
