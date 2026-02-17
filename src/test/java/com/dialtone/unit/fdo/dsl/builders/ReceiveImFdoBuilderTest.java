/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.builders.ReceiveImFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReceiveImFdoBuilder.
 */
@DisplayName("ReceiveImFdoBuilder")
class ReceiveImFdoBuilderTest {

    private static final ButtonTheme DEFAULT_THEME = new ButtonTheme(
        new int[]{192, 192, 192},  // face
        new int[]{0, 0, 0},        // text
        new int[]{255, 255, 255},  // top edge
        new int[]{128, 128, 128}   // bottom edge
    );

    @Test
    void shouldGenerateValidFdoSource() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
        ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(12345, "TestUser", "Hello world", 9999, DEFAULT_THEME);
        String source = builder.toSource(ctx);

        System.out.println("=== ReceiveImFdoBuilder Output ===");
        System.out.println(source);
        System.out.println("=== End Output ===");

        assertNotNull(source);
        assertTrue(source.contains("uni_start_stream"));
        assertTrue(source.contains("uni_end_stream"));
        assertTrue(source.contains("man_preset_gid"));
        assertTrue(source.contains("if_last_return_true_then"));
        assertTrue(source.contains("uni_sync_skip"));
        assertTrue(source.contains("ind_group"));
        assertTrue(source.contains("man_append_data"));
        assertTrue(source.contains("TestUser: Hello world"));
    }

    @Test
    void shouldContainMessageAppendAfterSyncSkip2() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
        ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(100, "Sender", "Test message", 50, DEFAULT_THEME);
        String source = builder.toSource(ctx);

        // The message append should happen after uni_sync_skip <2>
        int syncSkip2Index = source.indexOf("uni_sync_skip <2>");
        int appendDataIndex = source.indexOf("man_append_data");

        assertTrue(syncSkip2Index > 0, "Should contain uni_sync_skip <2>");
        assertTrue(appendDataIndex > syncSkip2Index, "man_append_data should come after uni_sync_skip <2>");

        // Verify the message is appended
        assertTrue(source.contains("Sender: Test message"), "Should contain formatted message");
    }

    @Test
    void shouldContainCorrectWindowStructure() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
        ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(100, "Sender", "Test", 50, DEFAULT_THEME);
        String source = builder.toSource(ctx);

        // Verify window creation elements
        assertTrue(source.contains("mat_object_id"), "Should have object ID");
        assertTrue(source.contains("mat_relative_tag <3>"), "Message view should have relative_tag 3");
        assertTrue(source.contains("man_set_context_relative <3>"), "Should set context to relative tag 3");
    }

    @Nested
    @DisplayName("Message whitespace trimming")
    class MessageWhitespaceTrimming {

        @Test
        @DisplayName("Should trim leading spaces from message")
        void shouldTrimLeadingSpaces() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "User", "   Hello", 1, null);
            String source = builder.toSource(ctx);

            assertTrue(source.contains("User: Hello"), "Leading spaces should be trimmed");
            assertFalse(source.contains("User:    Hello"), "Should not contain leading spaces");
        }

        @Test
        @DisplayName("Should trim trailing spaces from message")
        void shouldTrimTrailingSpaces() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "User", "Hello   ", 1, null);
            String source = builder.toSource(ctx);

            // Verify trimmed message appears (without trailing spaces before the line break)
            assertTrue(source.contains("User: Hello"), "Trailing spaces should be trimmed");
            assertFalse(source.contains("User: Hello   "), "Should not contain trailing spaces");
        }

        @Test
        @DisplayName("Should trim both leading and trailing spaces")
        void shouldTrimBothEnds() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "User", "   Hello World   ", 1, null);
            String source = builder.toSource(ctx);

            assertTrue(source.contains("User: Hello World"), "Both ends should be trimmed");
        }

        @Test
        @DisplayName("Should trim tabs and newlines")
        void shouldTrimTabsAndNewlines() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "User", "\t\nHello\n\t", 1, null);
            String source = builder.toSource(ctx);

            assertTrue(source.contains("User: Hello"), "Tabs and newlines should be trimmed");
        }

        @Test
        @DisplayName("Should preserve internal whitespace")
        void shouldPreserveInternalWhitespace() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "User", "Hello   World", 1, null);
            String source = builder.toSource(ctx);

            assertTrue(source.contains("User: Hello   World"), "Internal whitespace should be preserved");
        }

        @Test
        @DisplayName("Should handle whitespace-only message")
        void shouldHandleWhitespaceOnlyMessage() {
            ReceiveImFdoBuilder.Config config = new ReceiveImFdoBuilder.Config(
                1, "User", "   \t\n   ", 1, null);

            // Verify the message is trimmed to empty
            assertEquals("", config.message(), "Whitespace-only should become empty string");
        }

        @Test
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            ReceiveImFdoBuilder.Config config = new ReceiveImFdoBuilder.Config(
                1, "User", null, 1, null);

            // Verify null becomes empty string
            assertEquals("", config.message(), "Null should become empty string");
        }

        @Test
        @DisplayName("Should trim fromUser whitespace")
        void shouldTrimFromUserWhitespace() {
            RenderingContext ctx = RenderingContext.DEFAULT;
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "  Sender  ", "Hello", 1, null);
            String source = builder.toSource(ctx);

            assertTrue(source.contains("Sender: Hello"), "fromUser should be trimmed");
            assertTrue(source.contains("mat_title <\"Instant Message: Sender\">"),
                "Window title should use trimmed sender");
        }

        @Test
        @DisplayName("Config record should store trimmed values")
        void configShouldStoreTrimmedValues() {
            ReceiveImFdoBuilder.Config config = new ReceiveImFdoBuilder.Config(
                1, "  User  ", "  Message  ", 1, null);

            assertEquals("User", config.fromUser(), "fromUser should be trimmed in config");
            assertEquals("Message", config.message(), "message should be trimmed in config");
        }
    }
}
