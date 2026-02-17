/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.SendImEchoFdoBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SendImEchoFdoBuilder.
 * Verifies output matches the original fdo/send_im_echo_minimal.fdo.txt template.
 */
@DisplayName("SendImEchoFdoBuilder")
class SendImEchoFdoBuilderTest {

    private static final int TEST_WINDOW_ID = 42;
    private static final String TEST_FROM_USER = "TestUser";
    private static final String TEST_MESSAGE = "Hello, World!";
    private SendImEchoFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SendImEchoFdoBuilder(TEST_WINDOW_ID, TEST_FROM_USER, TEST_MESSAGE);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("send_im_echo_minimal", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }

        @Test
        @DisplayName("Should return config")
        void shouldReturnConfig() {
            SendImEchoFdoBuilder.Config config = builder.getConfig();
            assertNotNull(config);
            assertEquals(TEST_WINDOW_ID, config.windowId());
            assertEquals(TEST_FROM_USER, config.fromUser());
            assertEquals(TEST_MESSAGE, config.message());
        }
    }

    @Nested
    @DisplayName("FDO source generation")
    class FdoSourceGeneration {

        @Test
        @DisplayName("Should generate valid FDO source")
        void shouldGenerateValidFdoSource() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("Should contain stream start and end")
        void shouldContainStreamStartAndEnd() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_start_stream"), "Should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), "Should have stream end");
        }

        @Test
        @DisplayName("Should contain man_preset_gid for window check")
        void shouldContainPresetGid() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_preset_gid"), "Should have preset GID check");
        }

        @Test
        @DisplayName("Should contain if_last_return_true_then conditional")
        void shouldContainConditional() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("if_last_return_true_then"),
                "Should have conditional for window existence check");
        }

        @Test
        @DisplayName("Should contain man_set_context_globalid")
        void shouldContainSetContextGlobalId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_set_context_globalid") || fdo.contains("man_set_context_global_id"),
                "Should set context to window");
        }

        @Test
        @DisplayName("Should contain man_set_context_relative for view targeting")
        void shouldContainSetContextRelative() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_set_context_relative"),
                "Should target view with relative tag");
        }

        @Test
        @DisplayName("Should contain mat_paragraph")
        void shouldContainParagraph() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_paragraph"), "Should have paragraph formatting");
        }

        @Test
        @DisplayName("Should contain man_append_data with message")
        void shouldContainAppendDataWithMessage() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_append_data"), "Should append message data");
            assertTrue(fdo.contains(TEST_FROM_USER), "Should contain sender name");
            assertTrue(fdo.contains(TEST_MESSAGE), "Should contain message");
        }

        @Test
        @DisplayName("Should contain man_end_context")
        void shouldContainEndContext() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_end_context"), "Should end context");
        }

        @Test
        @DisplayName("Should contain man_make_focus")
        void shouldContainMakeFocus() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_make_focus"), "Should focus window");
        }

        @Test
        @DisplayName("Should contain man_update_display")
        void shouldContainUpdateDisplay() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_update_display"), "Should update display");
        }

        @Test
        @DisplayName("Should contain uni_sync_skip")
        void shouldContainSyncSkip() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_sync_skip"), "Should have sync skip target");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            String fdo1 = builder.toSource(RenderingContext.DEFAULT);
            String fdo2 = builder.toSource(RenderingContext.DEFAULT);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("echo() should create builder")
        void echoShouldCreateBuilder() {
            SendImEchoFdoBuilder echoBuilder = SendImEchoFdoBuilder.echo(100, "Sender", "Test message");
            String fdo = echoBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("Sender"));
            assertTrue(fdo.contains("Test message"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null fromUser as empty")
        void shouldHandleNullFromUser() {
            SendImEchoFdoBuilder nullBuilder = new SendImEchoFdoBuilder(1, null, "message");
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
        }

        @Test
        @DisplayName("Should handle null message as empty")
        void shouldHandleNullMessage() {
            SendImEchoFdoBuilder nullBuilder = new SendImEchoFdoBuilder(1, "user", null);
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> {
                new SendImEchoFdoBuilder(null);
            });
        }
    }

    @Nested
    @DisplayName("Message whitespace trimming")
    class MessageWhitespaceTrimming {

        @Test
        @DisplayName("Should trim leading and trailing spaces from message")
        void shouldTrimMessageWhitespace() {
            SendImEchoFdoBuilder.Config config = new SendImEchoFdoBuilder.Config(
                1, "User", "   Hello World   ");

            assertEquals("Hello World", config.message(), "Message should be trimmed");
        }

        @Test
        @DisplayName("Should trim leading and trailing spaces from fromUser")
        void shouldTrimFromUserWhitespace() {
            SendImEchoFdoBuilder.Config config = new SendImEchoFdoBuilder.Config(
                1, "   Sender   ", "Hello");

            assertEquals("Sender", config.fromUser(), "fromUser should be trimmed");
        }

        @Test
        @DisplayName("Should trim tabs and newlines")
        void shouldTrimTabsAndNewlines() {
            SendImEchoFdoBuilder.Config config = new SendImEchoFdoBuilder.Config(
                1, "\t\nUser\t\n", "\n\tMessage\n\t");

            assertEquals("User", config.fromUser(), "fromUser tabs/newlines should be trimmed");
            assertEquals("Message", config.message(), "message tabs/newlines should be trimmed");
        }

        @Test
        @DisplayName("Should preserve internal whitespace")
        void shouldPreserveInternalWhitespace() {
            SendImEchoFdoBuilder.Config config = new SendImEchoFdoBuilder.Config(
                1, "User", "Hello   World");

            assertEquals("Hello   World", config.message(), "Internal whitespace should be preserved");
        }

        @Test
        @DisplayName("Should handle whitespace-only values")
        void shouldHandleWhitespaceOnlyValues() {
            SendImEchoFdoBuilder.Config config = new SendImEchoFdoBuilder.Config(
                1, "   ", "   \t\n   ");

            assertEquals("", config.fromUser(), "Whitespace-only fromUser should become empty");
            assertEquals("", config.message(), "Whitespace-only message should become empty");
        }

        @Test
        @DisplayName("Trimmed values should appear in FDO output")
        void trimmedValuesShouldAppearInOutput() {
            SendImEchoFdoBuilder builder = new SendImEchoFdoBuilder(1, "  Sender  ", "  Hello  ");
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("Sender: Hello"), "FDO should contain trimmed values");
            assertFalse(fdo.contains("  Sender"), "FDO should not contain leading spaces");
        }
    }
}
