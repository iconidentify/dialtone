/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.bot;

/**
 * Context in which a bot is generating a response.
 * Used to adjust AI behavior and formatting based on communication channel.
 */
public enum ResponseContext {
    /**
     * Response in a public chat room.
     * Others can see the conversation. Bot should be aware multiple people may be present.
     */
    CHAT_ROOM,

    /**
     * Response in a private instant message.
     * One-on-one conversation. Bot should be more personal and direct.
     */
    INSTANT_MESSAGE
}
