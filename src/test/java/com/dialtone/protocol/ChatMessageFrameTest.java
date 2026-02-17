/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.protocol.chat.ChatTokenHandler;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.Pacer;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.auth.UserRegistry;
import com.dialtone.chat.bot.ChatBotRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for chat message frame building using proper P3 DATA frame structure.
 * All chat frames use type 0x20 (DATA) and go through the restamper.
 *
 * AA Frame (All chat messages - uses mat_relative_tag):
 * - Byte 0: 0x5A (magic)
 * - Bytes 1-6: 0x00 (set by restamper: CRC, length, TX, RX)
 * - Byte 7: 0x20 (DATA type)
 * - Bytes 8-9: 0x41 0x41 ("AA" token)
 * - Byte 10: mat_relative_tag (sender's global chat tag)
 * - Bytes 11+: bare message
 * - Last byte: 0x0D (terminator)
 * - Usage: ALL user and bot chat messages (sender echo + broadcast to recipients)
 *
 * AB Frame (System messages - uses explicit username):
 * - Byte 0: 0x5A (magic)
 * - Bytes 1-6: 0x00 (set by restamper: CRC, length, TX, RX)
 * - Byte 7: 0x20 (DATA type)
 * - Bytes 8-9: 0x41 0x42 ("AB" token)
 * - Bytes 10+: [10-char padded username]\0[message]
 * - Last byte: 0x0D (terminator)
 * - Usage: RESERVED for future system messages from "OnlineHost" (join/leave announcements)
 */
class ChatMessageFrameTest {

    private ChatTokenHandler chatHandler;

    @BeforeEach
    void setUp() {
        SessionContext session = new SessionContext();
        Pacer pacer = new Pacer(new com.dialtone.state.SequenceManager(), false);
        FdoCompiler fdoCompiler = new FdoCompiler(new Properties());
        FdoProcessor fdoProcessor = new FdoProcessor(fdoCompiler, pacer, 10);
        UserRegistry userRegistry = UserRegistry.getInstance();
        ChatBotRegistry botRegistry = ChatBotRegistry.getInstance();
        Properties props = new Properties();
        chatHandler = new ChatTokenHandler(session, pacer, fdoCompiler, fdoProcessor, userRegistry, botRegistry, props);
    }

    // ====================================================================
    // AA FRAME TESTS (All Chat Messages - Mat Relative Tag)
    // ====================================================================

    @Test
    void echoFrame_shouldHaveCorrectStructure() {
        // Given: A simple ASCII message and sender's global tag
        String message = "Hello World";
        int senderTag = 3; // Test global tag

        // When: Building an AA echo frame
        byte[] frame = chatHandler.buildChatMessageMatRelativeId(message, senderTag);

        // Then: Frame should match P3 DATA frame structure
        assertNotNull(frame, "Frame should not be null");
        assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");

        // Bytes 1-6 are zeros (will be set by restamper)
        assertEquals(0x00, frame[1] & 0xFF, "Byte 1 should be 0x00 (CRC_HI, set by restamper)");
        assertEquals(0x00, frame[2] & 0xFF, "Byte 2 should be 0x00 (CRC_LO, set by restamper)");
        assertEquals(0x00, frame[3] & 0xFF, "Byte 3 should be 0x00 (LEN_HI, set by restamper)");
        assertEquals(0x00, frame[4] & 0xFF, "Byte 4 should be 0x00 (LEN_LO, set by restamper)");
        assertEquals(0x00, frame[5] & 0xFF, "Byte 5 should be 0x00 (TX, set by restamper)");
        assertEquals(0x00, frame[6] & 0xFF, "Byte 6 should be 0x00 (RX, set by restamper)");

        // Type should be DATA (0x20) for restamping
        assertEquals(0x20, frame[7] & 0xFF, "Type should be 0x20 (DATA)");

        // Main token "AA" (0x41 0x41)
        assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A' (0x41)");
        assertEquals(0x41, frame[9] & 0xFF, "Token low should be 'A' (0x41)");

        // Mat relative tag (sender's global chat tag)
        assertEquals(senderTag, frame[10] & 0xFF, "Should have sender's global tag " + senderTag);

        // Terminator
        assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Frame should end with 0x0D");

        // Extract message (from byte 11 to length-1)
        byte[] messageBytes = new byte[frame.length - 12]; // 11-byte header + 1-byte terminator
        System.arraycopy(frame, 11, messageBytes, 0, messageBytes.length);
        String extractedMessage = new String(messageBytes, StandardCharsets.US_ASCII);

        assertEquals(message, extractedMessage, "Message should be bare (no username)");
    }

    @Test
    void echoFrame_shouldReplaceEmojisWithSpaces() {
        // Given: A message with emojis and sender's global tag
        String messageWithEmojis = "Hello World! 😀🎉";
        int senderTag = 4; // Test global tag

        // When: Building an AA echo frame
        byte[] frame = chatHandler.buildChatMessageMatRelativeId(messageWithEmojis, senderTag);

        // Then: Emojis should be replaced with space
        byte[] messageBytes = new byte[frame.length - 12];
        System.arraycopy(frame, 11, messageBytes, 0, messageBytes.length);
        String extractedMessage = new String(messageBytes, StandardCharsets.US_ASCII);

        assertEquals("Hello World!  ", extractedMessage, "Emojis should be replaced with space");
        assertFalse(extractedMessage.contains("😀"), "Frame should not contain emoji characters");

        // Verify mat_relative_tag is correct
        assertEquals(senderTag, frame[10] & 0xFF, "Should have sender's global tag " + senderTag);
    }

    @Test
    void echoFrame_shouldHandleEmptyMessage() {
        // Given: An empty message and sender's global tag
        String message = "";
        int senderTag = 5; // Test global tag

        // When: Building an AA echo frame
        byte[] frame = chatHandler.buildChatMessageMatRelativeId(message, senderTag);

        // Then: Frame should have valid structure with empty payload
        assertNotNull(frame, "Frame should not be null for empty message");
        assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");
        assertEquals(0x20, frame[7] & 0xFF, "Type should be 0x20 (DATA)");
        assertEquals(senderTag, frame[10] & 0xFF, "Should have sender's global tag " + senderTag);

        // Frame size: 11-byte header + 0 message + 1-byte terminator = 12 bytes
        assertEquals(12, frame.length, "Empty message frame should be 12 bytes");
    }

    @Test
    void echoFrame_shouldPreserveAsciiPunctuation() {
        // Given: A message with ASCII punctuation and sender's global tag
        String message = "Hello! How are you? I'm fine. :) #test @user";
        int senderTag = 6; // Test global tag

        // When: Building an AA echo frame
        byte[] frame = chatHandler.buildChatMessageMatRelativeId(message, senderTag);

        // Then: All ASCII punctuation should be preserved
        byte[] messageBytes = new byte[frame.length - 12];
        System.arraycopy(frame, 11, messageBytes, 0, messageBytes.length);
        String extractedMessage = new String(messageBytes, StandardCharsets.US_ASCII);

        assertEquals(message, extractedMessage, "ASCII punctuation should be preserved");

        // Verify mat_relative_tag is correct
        assertEquals(senderTag, frame[10] & 0xFF, "Should have sender's global tag " + senderTag);
    }

    // ====================================================================
    // AB FRAME TESTS (System Messages - Explicit Username)
    // Reserved for future "OnlineHost" system messages
    // ====================================================================

    @Test
    void receivedFrame_shouldHaveCorrectStructure() {
        // Given: A username and message
        String username = "Root";
        String message = "Hello World";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(username, message);

        // Then: Frame should match P3 DATA frame structure
        assertNotNull(frame, "Frame should not be null");
        assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");

        // Bytes 1-6 are zeros (will be set by restamper)
        assertEquals(0x00, frame[1] & 0xFF, "Byte 1 should be 0x00 (CRC_HI, set by restamper)");
        assertEquals(0x00, frame[2] & 0xFF, "Byte 2 should be 0x00 (CRC_LO, set by restamper)");
        assertEquals(0x00, frame[3] & 0xFF, "Byte 3 should be 0x00 (LEN_HI, set by restamper)");
        assertEquals(0x00, frame[4] & 0xFF, "Byte 4 should be 0x00 (LEN_LO, set by restamper)");
        assertEquals(0x00, frame[5] & 0xFF, "Byte 5 should be 0x00 (TX, set by restamper)");
        assertEquals(0x00, frame[6] & 0xFF, "Byte 6 should be 0x00 (RX, set by restamper)");

        // Type should be DATA (0x20) for restamping
        assertEquals(0x20, frame[7] & 0xFF, "Type should be 0x20 (DATA)");

        // Main token "AB" (0x41 0x42)
        assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A' (0x41)");
        assertEquals(0x42, frame[9] & 0xFF, "Token low should be 'B' (0x42)");

        // Terminator
        assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Frame should end with 0x0D");

        // Extract payload (starts at byte 10) - 10-byte header + 1-byte terminator
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Verify payload format: [10-char padded username]\0[message]
        byte[] messageBytes = message.getBytes(StandardCharsets.US_ASCII);

        // Extract username (always 10 bytes)
        byte[] extractedUsername = new byte[10];
        System.arraycopy(payloadBytes, 0, extractedUsername, 0, 10);
        String extractedName = new String(extractedUsername, StandardCharsets.US_ASCII).trim();
        assertEquals(username, extractedName, "Username should match after trimming padding spaces");

        // Check null terminator at position 10
        assertEquals(0x00, payloadBytes[10], "Null terminator at position 10");

        // Check message starts at position 11
        int messageStart = 11; // Fixed position after 10-char username + null
        for (int i = 0; i < messageBytes.length; i++) {
            assertEquals(messageBytes[i], payloadBytes[messageStart + i], "Message byte " + i + " should match");
        }
    }

    @Test
    void receivedFrame_shouldReplaceEmojisWithSpaces() {
        // Given: A message with emojis
        String username = "Grok";
        String messageWithEmojis = "Hello World! 😀🎉";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(username, messageWithEmojis);

        // Then: Emojis should be replaced with space in message portion
        // Extract payload (starts at byte 10)
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Extract message part (starts at position 11 - after 10-char username + null)
        int messageStart = 11; // Fixed position
        byte[] messageBytes = new byte[payloadBytes.length - messageStart];
        System.arraycopy(payloadBytes, messageStart, messageBytes, 0, messageBytes.length);
        String extractedMessage = new String(messageBytes, StandardCharsets.US_ASCII);

        assertEquals("Hello World!  ", extractedMessage, "Emojis should be replaced with space");
        assertFalse(extractedMessage.contains("😀"), "Frame should not contain emoji characters");
    }

    @Test
    void receivedFrame_shouldHandleLongUsernames() {
        // Given: A long username (16 chars - exceeds 10-char max)
        String longUsername = "VeryLongUsername"; // 16 chars
        String expectedTruncated = "VeryLongUs"; // Truncated to 10 chars
        String message = "Test message";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(longUsername, message);

        // Then: Username should be truncated to 10 chars
        assertNotNull(frame, "Frame should not be null");
        assertEquals(0x42, frame[9] & 0xFF, "Token low should be 'B' (0x42)");

        // Extract payload and verify truncated username
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Extract username (always 10 bytes)
        byte[] extractedUsername = new byte[10];
        System.arraycopy(payloadBytes, 0, extractedUsername, 0, 10);
        String extractedName = new String(extractedUsername, StandardCharsets.US_ASCII);

        assertEquals(expectedTruncated, extractedName, "Long username should be truncated to 10 chars");
    }

    @Test
    void receivedFrame_shouldHandleBotNames() {
        // Given: Bot usernames
        String botUsername = "Grok";
        String message = "Hey! What's up?";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(botUsername, message);

        // Then: Bot name should be in payload (padded to 10 chars)
        assertNotNull(frame, "Frame should not be null");
        assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A' (0x41)");
        assertEquals(0x42, frame[9] & 0xFF, "Token low should be 'B' (0x42)");

        // Extract and verify bot name from payload
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Extract username (always 10 bytes)
        byte[] extractedUsername = new byte[10];
        System.arraycopy(payloadBytes, 0, extractedUsername, 0, 10);
        String extractedName = new String(extractedUsername, StandardCharsets.US_ASCII).trim();

        assertEquals(botUsername, extractedName, "Bot username should match after trimming padding");
    }

    @Test
    void receivedFrame_shouldHandleComplexNonAscii() {
        // Given: A message with various non-ASCII characters
        String username = "TestUser";
        String messageWithUnicode = "Test 中文 🏆💯";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(username, messageWithUnicode);

        // Then: Non-ASCII characters should be replaced with spaces
        // Extract payload and message portion
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Message starts at position 11 (after 10-char username + null)
        int messageStart = 11; // Fixed position
        byte[] messageBytes = new byte[payloadBytes.length - messageStart];
        System.arraycopy(payloadBytes, messageStart, messageBytes, 0, messageBytes.length);
        String extractedMessage = new String(messageBytes, StandardCharsets.US_ASCII);

        assertEquals("Test    ", extractedMessage, "Non-ASCII should be replaced with spaces");
        assertFalse(extractedMessage.contains("中"), "Should not contain Chinese characters");
        assertFalse(extractedMessage.contains("🏆"), "Should not contain emojis");
    }

    @Test
    void receivedFrame_shouldMatchCapturedProtocol() {
        // Given: Message data from AB protocol format
        String username = "diz";
        String message = "other one be sending information";

        // When: Building an AB received frame
        byte[] frame = chatHandler.buildChatMessageWithUsername(username, message);

        // Then: Verify AB token and [10-char username]\0[message] format
        assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A' (0x41)");
        assertEquals(0x42, frame[9] & 0xFF, "Token low should be 'B' (0x42)");

        // Extract payload and verify structure
        byte[] payloadBytes = new byte[frame.length - 11];
        System.arraycopy(frame, 10, payloadBytes, 0, payloadBytes.length);

        // Extract username (always 10 bytes)
        byte[] extractedUsername = new byte[10];
        System.arraycopy(payloadBytes, 0, extractedUsername, 0, 10);
        String extractedName = new String(extractedUsername, StandardCharsets.US_ASCII).trim();
        assertEquals(username, extractedName, "Username should match after trimming padding");

        // Verify null terminator at position 10
        assertEquals(0x00, payloadBytes[10], "Null terminator at position 10");

        // Verify message starts at position 11
        int messageStart = 11; // Fixed position
        byte[] extractedMessageBytes = new byte[message.length()];
        System.arraycopy(payloadBytes, messageStart, extractedMessageBytes, 0, message.length());
        String extractedMessage = new String(extractedMessageBytes, StandardCharsets.US_ASCII);

        assertEquals(message, extractedMessage, "Message should match");
    }
}
