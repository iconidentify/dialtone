/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.chat;

import com.dialtone.protocol.chat.ChatFrameBuilder;
import com.dialtone.protocol.chat.ChatTokenHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatFrameBuilder.
 * Tests chat frame construction for AA, AB, CA, CB tokens.
 */
@DisplayName("ChatFrameBuilder")
class ChatFrameBuilderTest {

    private ChatFrameBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ChatFrameBuilder("[TEST] ");
    }

    @Nested
    @DisplayName("buildChatMessageMatRelativeId()")
    class BuildChatMessageMatRelativeId {

        @Test
        @DisplayName("should build valid AA frame structure")
        void shouldBuildValidFrame() {
            byte[] frame = builder.buildChatMessageMatRelativeId("Hello", 5);

            // Check frame structure
            assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");
            assertEquals(0x20, frame[7] & 0xFF, "Type should be DATA (0x20)");
            assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A'");
            assertEquals(0x41, frame[9] & 0xFF, "Token low should be 'A' (AA)");
            assertEquals(5, frame[10] & 0xFF, "Mat relative tag should match");
            assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Should end with terminator");
        }

        @Test
        @DisplayName("should include message in payload")
        void shouldIncludeMessage() {
            String message = "Hello World";
            byte[] frame = builder.buildChatMessageMatRelativeId(message, 1);

            // Extract message from frame (after header at byte 11)
            String extracted = new String(frame, 11, message.length(), StandardCharsets.US_ASCII);
            assertEquals(message, extracted);
        }

        @Test
        @DisplayName("should calculate correct frame length")
        void shouldCalculateCorrectLength() {
            String message = "Test";
            byte[] frame = builder.buildChatMessageMatRelativeId(message, 1);

            // 11-byte header + message length + 1-byte terminator
            assertEquals(11 + message.length() + 1, frame.length);
        }

        @Test
        @DisplayName("should replace non-ASCII characters with spaces")
        void shouldReplaceNonAscii() {
            String message = "Hello World";
            byte[] frame = builder.buildChatMessageMatRelativeId(message, 1);

            // Emoji should be replaced with space
            String extracted = new String(frame, 11, message.length() + 1, StandardCharsets.US_ASCII);
            assertFalse(extracted.contains("\uD83D\uDE00"));
            assertTrue(extracted.contains(" "));
        }

        @Test
        @DisplayName("should handle empty message")
        void shouldHandleEmptyMessage() {
            byte[] frame = builder.buildChatMessageMatRelativeId("", 1);

            // 11-byte header + 0-byte message + 1-byte terminator
            assertEquals(12, frame.length);
        }
    }

    @Nested
    @DisplayName("buildChatMessageWithUsername()")
    class BuildChatMessageWithUsername {

        @Test
        @DisplayName("should build valid AB frame structure")
        void shouldBuildValidFrame() {
            byte[] frame = builder.buildChatMessageWithUsername("TestUser", "Hello");

            assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");
            assertEquals(0x20, frame[7] & 0xFF, "Type should be DATA (0x20)");
            assertEquals(0x41, frame[8] & 0xFF, "Token high should be 'A'");
            assertEquals(0x42, frame[9] & 0xFF, "Token low should be 'B' (AB)");
            assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Should end with terminator");
        }

        @Test
        @DisplayName("should pad username to 10 characters")
        void shouldPadUsername() {
            byte[] frame = builder.buildChatMessageWithUsername("Bob", "Hi");

            // Username starts at byte 10, padded to 10 chars
            String paddedName = new String(frame, 10, 10, StandardCharsets.US_ASCII);
            assertEquals("Bob       ", paddedName);
        }

        @Test
        @DisplayName("should truncate long username to 10 characters")
        void shouldTruncateLongUsername() {
            byte[] frame = builder.buildChatMessageWithUsername("VeryLongUsername", "Hi");

            // Username should be truncated
            String truncatedName = new String(frame, 10, 10, StandardCharsets.US_ASCII);
            assertEquals("VeryLongUs", truncatedName);
        }

        @Test
        @DisplayName("should include null separator after username")
        void shouldIncludeNullSeparator() {
            byte[] frame = builder.buildChatMessageWithUsername("Bob", "Hi");

            // Null at position 20 (10 header + 10 username)
            assertEquals(0x00, frame[20]);
        }

        @Test
        @DisplayName("should include message after username")
        void shouldIncludeMessage() {
            String message = "Hello";
            byte[] frame = builder.buildChatMessageWithUsername("Bob", message);

            // Message starts at position 21 (10 header + 10 username + 1 null)
            String extracted = new String(frame, 21, message.length(), StandardCharsets.US_ASCII);
            assertEquals(message, extracted);
        }
    }

    @Nested
    @DisplayName("buildChatNotificationFrame()")
    class BuildChatNotificationFrame {

        @Test
        @DisplayName("should build valid arrival notification frame")
        void shouldBuildArrivalFrame() {
            byte[] frame = builder.buildChatNotificationFrame(
                "TestUser", 5, ChatTokenHandler.ChatEventType.ARRIVAL);

            assertEquals(0x5A, frame[0] & 0xFF, "Magic byte should be 0x5A");
            assertEquals(0x1B, frame[1] & 0xFF, "Type should be 0x1B");
            assertEquals(0x74, frame[2] & 0xFF, "Stream ID high should be 0x74");
            assertEquals(0x00, frame[3] & 0xFF, "Stream ID low should be 0x00");
            assertEquals(0x0D, frame[frame.length - 1] & 0xFF, "Should end with terminator");
        }

        @Test
        @DisplayName("should include CA token for arrival")
        void shouldIncludeCaForArrival() {
            byte[] frame = builder.buildChatNotificationFrame(
                "User", 1, ChatTokenHandler.ChatEventType.ARRIVAL);

            // Token is at positions 5-6 ("mS"), then " CA" at 7-9
            assertEquals(0x43, frame[8] & 0xFF, "'C'");
            assertEquals(0x41, frame[9] & 0xFF, "'A' for arrival");
        }

        @Test
        @DisplayName("should include CB token for departure")
        void shouldIncludeCbForDeparture() {
            byte[] frame = builder.buildChatNotificationFrame(
                "User", 1, ChatTokenHandler.ChatEventType.DEPARTURE);

            assertEquals(0x43, frame[8] & 0xFF, "'C'");
            assertEquals(0x42, frame[9] & 0xFF, "'B' for departure");
        }

        @Test
        @DisplayName("should include mat relative tag")
        void shouldIncludeMatRelativeTag() {
            byte[] frame = builder.buildChatNotificationFrame(
                "User", 42, ChatTokenHandler.ChatEventType.ARRIVAL);

            assertEquals(42, frame[10] & 0xFF, "Mat relative tag should match");
        }

        @Test
        @DisplayName("should include username in payload")
        void shouldIncludeUsername() {
            String username = "Bobby";
            byte[] frame = builder.buildChatNotificationFrame(
                username, 1, ChatTokenHandler.ChatEventType.ARRIVAL);

            // Username starts after mat relative tag (position 11)
            String extracted = new String(frame, 11, username.length(), StandardCharsets.US_ASCII);
            assertEquals(username, extracted);
        }
    }
}
