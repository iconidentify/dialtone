/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.utils;

import com.dialtone.utils.MessageSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MessageSplitter utility class.
 * Ensures proper 92-character message splitting to prevent Windows client crashes.
 */
public class MessageSplitterTest {

    @Test
    void shouldReturnEmptyListForNullMessage() {
        List<String> chunks = MessageSplitter.splitMessage(null);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForEmptyMessage() {
        List<String> chunks = MessageSplitter.splitMessage("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldReturnSingleChunkForShortMessage() {
        String shortMessage = "Hello world!";
        List<String> chunks = MessageSplitter.splitMessage(shortMessage);

        assertEquals(1, chunks.size());
        assertEquals(shortMessage, chunks.get(0));
        assertTrue(chunks.get(0).length() <= MessageSplitter.CHAT_MAX_LENGTH);
    }

    @Test
    void shouldReturnSingleChunkFor92CharacterMessage() {
        // Exactly 92 characters
        String message = "A".repeat(92);
        List<String> chunks = MessageSplitter.splitMessage(message);

        assertEquals(1, chunks.size());
        assertEquals(message, chunks.get(0));
        assertEquals(92, chunks.get(0).length());
    }

    @Test
    void shouldSplitLongMessageIntoMultipleChunks() {
        // 200 characters should be split into multiple chunks
        String longMessage = "A".repeat(200);
        List<String> chunks = MessageSplitter.splitMessage(longMessage);

        assertTrue(chunks.size() > 1);

        // All chunks should be <= 92 characters
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH,
                "Chunk length " + chunk.length() + " exceeds maximum " + MessageSplitter.CHAT_MAX_LENGTH);
        }

        // Concatenated chunks should equal original message
        String reconstructed = String.join("", chunks);
        assertEquals(longMessage, reconstructed);
    }

    @Test
    void shouldPreserveWordBoundariesWhenPossible() {
        String message = "This is a test message with multiple words that should be split at word boundaries when possible";
        List<String> chunks = MessageSplitter.splitMessage(message);

        assertTrue(chunks.size() > 1);

        // All chunks should be <= 92 characters
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH);
        }

        // Most chunks should not end in the middle of a word (except if forced by long words)
        for (int i = 0; i < chunks.size() - 1; i++) {
            String chunk = chunks.get(i);
            // If chunk doesn't end with a space and is exactly at the limit,
            // it might be a forced split of a very long word - that's acceptable
            if (chunk.length() < MessageSplitter.CHAT_MAX_LENGTH) {
                assertTrue(chunk.endsWith(" ") || Character.isWhitespace(chunk.charAt(chunk.length() - 1)),
                    "Chunk should end at word boundary when not at character limit: '" + chunk + "'");
            }
        }

        // Concatenated chunks (with proper spacing) should preserve the original meaning
        String reconstructed = String.join("", chunks).trim();
        assertEquals(message, reconstructed);
    }

    @Test
    void shouldHandleVeryLongWordsCorrectly() {
        // Create a word longer than 92 characters
        String veryLongWord = "supercalifragilisticexpialidocious".repeat(3); // ~102 characters
        List<String> chunks = MessageSplitter.splitMessage(veryLongWord);

        assertTrue(chunks.size() > 1);

        // All chunks should be <= 92 characters
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH);
        }

        // Concatenated chunks should equal original message
        String reconstructed = String.join("", chunks);
        assertEquals(veryLongWord, reconstructed);
    }

    @Test
    void shouldGetCorrectChunkCount() {
        // Test various message lengths
        assertEquals(0, MessageSplitter.getChunkCount(""));
        assertEquals(1, MessageSplitter.getChunkCount("Short message"));
        assertEquals(1, MessageSplitter.getChunkCount("A".repeat(92)));
        assertTrue(MessageSplitter.getChunkCount("A".repeat(200)) > 1);
    }

    @Test
    void shouldHandleMessageWithOnlySpaces() {
        String spacesOnly = " ".repeat(100);
        List<String> chunks = MessageSplitter.splitMessage(spacesOnly);

        assertTrue(chunks.size() >= 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH);
        }
    }

    @Test
    void shouldHandleNewlinesAndSpecialCharacters() {
        String messageWithNewlines = "Line 1\nLine 2\nLine 3 with some additional text that makes this message quite long and requires splitting";
        List<String> chunks = MessageSplitter.splitMessage(messageWithNewlines);

        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH);
        }

        String reconstructed = String.join("", chunks);
        assertEquals(messageWithNewlines, reconstructed);
    }

    @Test
    void shouldHandleGrokResponseLikeMessage() {
        // Simulate a typical long Grok response
        String grokResponse = "Here's a detailed explanation about the Dialtone project architecture: " +
                             "Dialtone is a Netty-based v3 protocol server that implements the proprietary AOL Instant Messenger protocol stack. " +
                             "It features dual-server architecture with both an AOL protocol server and a web management interface. " +
                             "The system handles FDO for dynamic UI generation, DOD for art asset delivery, and real-time messaging.";

        List<String> chunks = MessageSplitter.splitMessage(grokResponse);

        // Should be split into multiple chunks
        assertTrue(chunks.size() > 1);

        // All chunks should be within limit
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH,
                "Chunk: '" + chunk + "' (length: " + chunk.length() + ")");
        }

        // Should preserve content
        String reconstructed = String.join("", chunks).trim();
        assertEquals(grokResponse.trim(), reconstructed);

        System.out.println("Grok response split into " + chunks.size() + " chunks:");
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("  Chunk " + (i + 1) + " (" + chunks.get(i).length() + " chars): '" + chunks.get(i).trim() + "'");
        }
    }

    @Test
    void shouldHandleGrokResponseWithChatLimit() {
        // Simulate a typical long Grok response using chat room limit
        String grokResponse = "Here's a detailed explanation about the Dialtone project architecture: " +
                             "Dialtone is a Netty-based v3 protocol server that implements the proprietary AOL Instant Messenger protocol stack. " +
                             "It features dual-server architecture with both an AOL protocol server and a web management interface. " +
                             "The system handles FDO for dynamic UI generation, DOD for art asset delivery, and real-time messaging.";

        List<String> chunks = MessageSplitter.splitMessage(grokResponse, MessageSplitter.CHAT_MAX_LENGTH);

        // Should be split into multiple chunks
        assertTrue(chunks.size() > 1);

        // All chunks should be within chat limit
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MessageSplitter.CHAT_MAX_LENGTH,
                "Chat chunk: '" + chunk + "' (length: " + chunk.length() + ") exceeds " + MessageSplitter.CHAT_MAX_LENGTH + " chars");
        }

        // Should preserve content
        String reconstructed = String.join("", chunks).trim();
        assertEquals(grokResponse.trim(), reconstructed);
    }

    @Test
    void shouldSplitWithCustomLimit() {
        // Test with a custom limit
        String testMessage = "Hey, 1995 was a blockbuster year for the movies! Based on worldwide box office data";
        int customLimit = 50;

        List<String> chunks = MessageSplitter.splitMessage(testMessage, customLimit);

        // Should be split due to custom limit
        assertTrue(chunks.size() > 1, "Message should be split into multiple chunks");

        // All chunks should be within the custom limit
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= customLimit,
                "Chunk: '" + chunk + "' (length: " + chunk.length() + ") exceeds " + customLimit + " chars");
        }

        // Should preserve content
        String reconstructed = String.join("", chunks).trim();
        assertEquals(testMessage.trim(), reconstructed);
    }
}