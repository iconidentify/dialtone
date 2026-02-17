/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.bot;

/**
 * Interface for virtual users (bots) that participate in chat rooms.
 * Virtual users can respond to messages, maintain presence in rooms,
 * and provide automated assistance or entertainment.
 */
public interface VirtualUser {

    /**
     * Get the bot's display username.
     * This name will appear in chat room participant lists and message attributions.
     *
     * @return The bot's username (must be unique and non-null)
     */
    String getUsername();

    /**
     * Determine if the bot should respond to a given message.
     * Bots typically respond to @mentions, direct questions, or specific keywords.
     *
     * @param message The message content
     * @param sender The username of the sender
     * @return true if the bot should generate a response
     */
    boolean shouldRespondTo(String message, String sender);

    /**
     * Generate a response to a message.
     * This method should be non-blocking or have reasonable timeouts.
     *
     * @param message The message content
     * @param sender The username of the sender
     * @param context Additional context about the conversation
     * @return The bot's response, or null if unable to respond
     */
    String generateResponse(String message, String sender, ChatContext context);

    /**
     * Called when the bot joins a chat room.
     * Bots can use this to send a greeting or initialize room-specific state.
     *
     * @param roomName The name of the chat room
     */
    default void onJoinChatRoom(String roomName) {
        // Default: no action
    }

    /**
     * Called when the bot leaves a chat room.
     * Bots can use this to clean up room-specific state.
     *
     * @param roomName The name of the chat room
     */
    default void onLeaveChatRoom(String roomName) {
        // Default: no action
    }

    /**
     * Get a brief description of the bot's capabilities.
     * Used for help messages and documentation.
     *
     * @return A human-readable description of what the bot does
     */
    default String getDescription() {
        return "A virtual chat room participant";
    }

    /**
     * Check if the bot is currently active and able to respond.
     *
     * @return true if the bot is operational
     */
    default boolean isActive() {
        return true;
    }
}
