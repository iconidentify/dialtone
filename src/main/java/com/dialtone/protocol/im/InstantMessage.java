/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.im;

/**
 * Represents an instant message extracted from an iS token.
 *
 * <p>Message content is automatically trimmed of leading/trailing whitespace
 * (including newlines) to ensure clean display in IM windows.</p>
 *
 * @param recipient The username of the message recipient
 * @param message The HTML-formatted message content (trimmed of leading/trailing whitespace)
 * @param responseId The response ID for message tracking and window matching (may be null).
 *                   Must comply with 16-bit unsigned constraint (1-65535) for AOL client compatibility.
 *                   Server-generated IDs use range 10000-65535 with wraparound to avoid client conflicts.
 *                   Client-tracking IDs typically use range 1-1000.
 */
public record InstantMessage(String recipient, String message, Integer responseId) {

    /** AOL uses DEL (0x7F) as line terminator - not stripped by Java's strip() */
    private static final char AOL_LINE_TERMINATOR = '\u007F';

    public InstantMessage {
        // Trim leading/trailing whitespace from message (including newlines)
        // to ensure clean display in IM windows
        if (message != null) {
            message = stripAolWhitespace(message);
        }
        // Also trim recipient for safety
        if (recipient != null) {
            recipient = stripAolWhitespace(recipient);
        }
    }

    /**
     * Strip both standard whitespace AND AOL-specific characters (0x7F line terminator).
     */
    private static String stripAolWhitespace(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        // First strip standard whitespace
        s = s.strip();
        // Then strip AOL line terminators from both ends
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == AOL_LINE_TERMINATOR) {
            start++;
        }
        while (end > start && s.charAt(end - 1) == AOL_LINE_TERMINATOR) {
            end--;
        }
        return (start > 0 || end < s.length()) ? s.substring(start, end).strip() : s;
    }
}
