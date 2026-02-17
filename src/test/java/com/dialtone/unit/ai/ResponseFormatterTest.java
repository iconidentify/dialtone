/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.ai;

import com.dialtone.ai.ResponseFormatter;
import com.dialtone.ai.ResponseFormatter.FormattedResponse;
import com.dialtone.chat.bot.ResponseContext;
import com.dialtone.utils.MessageSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResponseFormatter.
 * Tests the three-layer response formatting approach:
 * 1. Pass-through for short messages
 * 2. AI splitting (mocked/disabled for unit tests)
 * 3. Deterministic fallback with word-boundary truncation
 */
class ResponseFormatterTest {

    private ResponseFormatter formatter;
    private Properties properties;

    @BeforeEach
    void setUp() {
        properties = new Properties();
        properties.setProperty("formatter.chat.room.limit", "68");
        properties.setProperty("formatter.im.limit", "512");
        properties.setProperty("formatter.split.delay.ms", "500");
        // Disable AI splitting for unit tests (no mock grokService)
        properties.setProperty("formatter.ai.splitting.enabled", "false");

        // Create formatter without grok service (AI splitting disabled)
        formatter = new ResponseFormatter(null, properties);
    }

    @Test
    void getLimitShouldReturn68ForChatRoom() {
        assertEquals(68, formatter.getLimit(ResponseContext.CHAT_ROOM));
    }

    @Test
    void getLimitShouldReturn512ForInstantMessage() {
        assertEquals(512, formatter.getLimit(ResponseContext.INSTANT_MESSAGE));
    }

    @Test
    void formatShouldReturnEmptyListForNullResponse() {
        FormattedResponse result = formatter.format(null, ResponseContext.CHAT_ROOM);

        assertTrue(result.messages().isEmpty());
        assertFalse(result.wasAiSplit());
        assertFalse(result.wasTruncated());
    }

    @Test
    void formatShouldReturnEmptyListForEmptyResponse() {
        FormattedResponse result = formatter.format("", ResponseContext.CHAT_ROOM);

        assertTrue(result.messages().isEmpty());
        assertFalse(result.wasAiSplit());
        assertFalse(result.wasTruncated());
    }

    @Test
    void formatShouldReturnSingleMessageWhenUnderChatRoomLimit() {
        String shortMessage = "This is a short response.";  // 26 chars

        FormattedResponse result = formatter.format(shortMessage, ResponseContext.CHAT_ROOM);

        assertTrue(result.isSingleMessage());
        assertEquals(1, result.messages().size());
        assertEquals(shortMessage, result.messages().get(0));
        assertFalse(result.wasAiSplit());
        assertFalse(result.wasTruncated());
        assertEquals(0, result.delayBetweenMs());  // No delay for single message
    }

    @Test
    void formatShouldReturnSingleMessageWhenUnderIMLimit() {
        String shortMessage = "This is a slightly longer response for IM.";  // 43 chars

        FormattedResponse result = formatter.format(shortMessage, ResponseContext.INSTANT_MESSAGE);

        assertTrue(result.isSingleMessage());
        assertEquals(shortMessage, result.messages().get(0));
        assertFalse(result.wasAiSplit());
        assertFalse(result.wasTruncated());
    }

    @Test
    void formatShouldSplitLongChatRoomResponse() {
        // Create a message that exceeds 68 chars
        String longMessage = "This is a very long response that exceeds the 68 character limit for chat rooms and needs to be split into multiple parts.";

        FormattedResponse result = formatter.format(longMessage, ResponseContext.CHAT_ROOM);

        assertFalse(result.isSingleMessage());
        assertTrue(result.messages().size() >= 2);
        // All parts should be under the limit
        for (String part : result.messages()) {
            assertTrue(part.length() <= 68,
                "Part length " + part.length() + " exceeds limit 68: " + part);
        }
        // Delay should be set for multi-part response
        assertEquals(500, result.delayBetweenMs());
    }

    @Test
    void formatShouldSplitLongIMResponse() {
        // Create a message that exceeds 512 chars
        String longMessage = "x".repeat(600);

        FormattedResponse result = formatter.format(longMessage, ResponseContext.INSTANT_MESSAGE);

        assertFalse(result.isSingleMessage());
        assertTrue(result.messages().size() >= 2);
        // All parts should be under the limit
        for (String part : result.messages()) {
            assertTrue(part.length() <= 512,
                "Part length " + part.length() + " exceeds limit 512: " + part);
        }
    }

    @Test
    void formatShouldPreserveWordBoundaries() {
        // Message designed to test word boundary splitting
        String message = "Hello world this is a test of word boundary splitting behavior";

        FormattedResponse result = formatter.format(message, ResponseContext.CHAT_ROOM);

        // The splitter should not cut words in half
        for (String part : result.messages()) {
            // Check that parts don't start with a word fragment
            // (a word fragment would be lowercase following the truncation)
            assertFalse(part.matches("^[a-z].*") && !part.startsWith("a "),
                "Part appears to start with word fragment: " + part);
        }
    }

    @Test
    void needsSplittingShouldReturnTrueWhenOverLimit() {
        String chatOverLimit = "x".repeat(100);  // Over 68 for chat
        String imOverLimit = "x".repeat(600);    // Over 512 for IM

        assertTrue(formatter.needsSplitting(chatOverLimit, ResponseContext.CHAT_ROOM));
        assertTrue(formatter.needsSplitting(imOverLimit, ResponseContext.INSTANT_MESSAGE));
    }

    @Test
    void needsSplittingShouldReturnFalseWhenUnderLimit() {
        String shortMessage = "Short message";

        assertFalse(formatter.needsSplitting(shortMessage, ResponseContext.CHAT_ROOM));
        assertFalse(formatter.needsSplitting(shortMessage, ResponseContext.INSTANT_MESSAGE));
    }

    @Test
    void needsSplittingShouldReturnFalseForNull() {
        assertFalse(formatter.needsSplitting(null, ResponseContext.CHAT_ROOM));
    }

    @Test
    void getSplitDelayMsShouldReturnConfiguredValue() {
        assertEquals(500, formatter.getSplitDelayMs());
    }

    @Test
    void formatShouldUseDifferentLimitsPerContext() {
        // Message that fits in IM (512) but not chat (68)
        String message = "This message is exactly long enough to require splitting in chat but not IM.";
        // Let's make it exactly the right length
        String chatOverLimit = "x".repeat(70);  // Over 68, needs split in chat
        String imUnderLimit = "x".repeat(400);  // Under 512, no split in IM

        FormattedResponse chatResult = formatter.format(chatOverLimit, ResponseContext.CHAT_ROOM);
        FormattedResponse imResult = formatter.format(imUnderLimit, ResponseContext.INSTANT_MESSAGE);

        // Chat should be split
        assertFalse(chatResult.isSingleMessage());
        // IM should not be split
        assertTrue(imResult.isSingleMessage());
    }

    @Test
    void formatShouldHandleExactlyAtLimit() {
        // Exactly 68 chars for chat
        String exactChatLimit = "x".repeat(68);

        FormattedResponse result = formatter.format(exactChatLimit, ResponseContext.CHAT_ROOM);

        assertTrue(result.isSingleMessage());
        assertEquals(exactChatLimit, result.messages().get(0));
    }

    @Test
    void formatShouldHandleOneCharOverLimit() {
        // 69 chars - one over the limit
        String oneOver = "x".repeat(69);

        FormattedResponse result = formatter.format(oneOver, ResponseContext.CHAT_ROOM);

        // Should split since it's over
        assertFalse(result.isSingleMessage());
    }

    @Test
    void formattedResponseRecordShouldReportSingleMessageCorrectly() {
        FormattedResponse single = new FormattedResponse(
            java.util.List.of("single message"), 0, false, false);
        FormattedResponse multi = new FormattedResponse(
            java.util.List.of("part 1", "part 2"), 500, false, false);

        assertTrue(single.isSingleMessage());
        assertFalse(multi.isSingleMessage());
    }

    @Test
    void formatShouldSplitLongStringWithNoSpaces() {
        // Very long continuous string with no spaces
        // The splitter will hard-cut at the limit since there are no word boundaries
        String noSpaces = "x".repeat(200);

        FormattedResponse result = formatter.format(noSpaces, ResponseContext.CHAT_ROOM);

        // Should have multiple parts
        assertFalse(result.isSingleMessage());
        assertTrue(result.messages().size() >= 2);
        // Each part should be under the limit
        for (String part : result.messages()) {
            assertTrue(part.length() <= 68,
                "Part length " + part.length() + " exceeds limit 68");
        }
    }

    @Test
    void formatShouldUseDefaultLimitsFromMessageSplitter() {
        // Test with default properties
        Properties defaultProps = new Properties();
        ResponseFormatter defaultFormatter = new ResponseFormatter(null, defaultProps);

        assertEquals(MessageSplitter.CHAT_MAX_LENGTH,
            defaultFormatter.getLimit(ResponseContext.CHAT_ROOM));
        assertEquals(MessageSplitter.IM_MAX_LENGTH,
            defaultFormatter.getLimit(ResponseContext.INSTANT_MESSAGE));
    }

    @Test
    void formatShouldNotReturnEmptyParts() {
        String message = "This is a normal message that gets split into parts";

        FormattedResponse result = formatter.format(message, ResponseContext.CHAT_ROOM);

        for (String part : result.messages()) {
            assertFalse(part.isEmpty(), "Found empty part in split result");
            assertFalse(part.isBlank(), "Found blank part in split result");
        }
    }

    @Test
    void formatShouldCapMessagesAtConfiguredLimit() {
        // Create formatter with max 3 messages
        Properties cappedProps = new Properties();
        cappedProps.setProperty("formatter.chat.room.limit", "68");
        cappedProps.setProperty("formatter.max.messages", "3");
        cappedProps.setProperty("formatter.ai.splitting.enabled", "false");
        ResponseFormatter cappedFormatter = new ResponseFormatter(null, cappedProps);

        // Create a very long message that would normally split into many parts
        String longMessage = "This is sentence one. This is sentence two. This is sentence three. " +
            "This is sentence four. This is sentence five. This is sentence six. " +
            "This is sentence seven. This is sentence eight. This is sentence nine.";

        FormattedResponse result = cappedFormatter.format(longMessage, ResponseContext.CHAT_ROOM);

        // Should be capped at 3 messages
        assertTrue(result.messages().size() <= 3,
            "Expected at most 3 messages but got " + result.messages().size());
    }

    @Test
    void getMaxMessagesShouldReturnConfiguredValue() {
        Properties props = new Properties();
        props.setProperty("formatter.max.messages", "5");
        ResponseFormatter customFormatter = new ResponseFormatter(null, props);

        assertEquals(5, customFormatter.getMaxMessages());
    }

    @Test
    void getMaxMessagesShouldDefaultTo10() {
        Properties emptyProps = new Properties();
        ResponseFormatter defaultFormatter = new ResponseFormatter(null, emptyProps);

        assertEquals(10, defaultFormatter.getMaxMessages());
    }
}
