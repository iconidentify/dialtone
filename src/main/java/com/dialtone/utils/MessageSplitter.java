/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting long messages into smaller chunks to prevent client crashes.
 * Designed specifically for AOL protocol compatibility.
 */
public class MessageSplitter {

    /**
     * Maximum characters for chat room messages.
     * Chat rooms have a 92 character limit per message.
     */
    public static final int CHAT_MAX_LENGTH = 92;

    /**
     * Maximum characters for instant messages.
     * IM windows support up to 512 characters per message.
     */
    public static final int IM_MAX_LENGTH = 512;


    /**
     * Splits a long message into chunks for chat room messages (max 92 characters),
     * preserving word boundaries where possible to maintain readability.
     *
     * @param message The message to split
     * @return List of message chunks, each no longer than CHAT_MAX_LENGTH
     */
    public static List<String> splitMessage(String message) {
        return splitMessage(message, CHAT_MAX_LENGTH);
    }

    /**
     * Splits a long message into chunks of specified maximum length, preserving
     * word boundaries where possible to maintain readability.
     *
     * @param message The message to split
     * @param maxLength Maximum length per chunk
     * @return List of message chunks, each no longer than maxLength
     */
    public static List<String> splitMessage(String message, int maxLength) {
        List<String> chunks = new ArrayList<>();

        if (message == null || message.isEmpty()) {
            return chunks;
        }

        // If message is already short enough, return as-is
        if (message.length() <= maxLength) {
            chunks.add(message);
            return chunks;
        }

        String remaining = message;

        while (remaining.length() > maxLength) {
            String chunk = extractChunk(remaining, maxLength);
            chunks.add(chunk);

            // Skip the chunk plus any following spaces to avoid duplicate spaces
            int nextStart = chunk.length();
            while (nextStart < remaining.length() && remaining.charAt(nextStart) == ' ') {
                nextStart++;
            }
            remaining = remaining.substring(nextStart);
        }

        // Add any remaining text as the final chunk
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    /**
     * Extracts a single chunk from the beginning of the text, trying to preserve
     * word boundaries where possible.
     *
     * @param text The text to extract a chunk from
     * @param maxLength Maximum length for the chunk
     * @return A chunk of text no longer than maxLength
     */
    private static String extractChunk(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        // Find the last space within the limit to preserve word boundaries
        String candidate = text.substring(0, maxLength);
        int lastSpace = candidate.lastIndexOf(' ');

        // If we found a space and it's not too close to the beginning,
        // split at the word boundary (include the space at the end of the chunk)
        if (lastSpace > maxLength / 3) {
            return text.substring(0, lastSpace + 1); // +1 to include the space
        }

        // If no good word boundary found, or the word is very long,
        // split at the character limit
        return text.substring(0, maxLength);
    }

    /**
     * Convenience method to get the total number of chunks a message would be split into
     * without actually performing the split.
     *
     * @param message The message to analyze
     * @return Number of chunks the message would be split into
     */
    public static int getChunkCount(String message) {
        return splitMessage(message).size();
    }

    /**
     * Convenience method to get the total number of chunks a message would be split into
     * using a custom maximum length without actually performing the split.
     *
     * @param message The message to analyze
     * @param maxLength Maximum length per chunk
     * @return Number of chunks the message would be split into
     */
    public static int getChunkCount(String message, int maxLength) {
        return splitMessage(message, maxLength).size();
    }
}