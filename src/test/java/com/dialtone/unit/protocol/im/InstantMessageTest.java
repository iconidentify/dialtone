/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.im;

import com.dialtone.protocol.im.InstantMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstantMessage record.
 * Verifies whitespace trimming and null handling.
 */
@DisplayName("InstantMessage")
class InstantMessageTest {

    @Nested
    @DisplayName("Message whitespace trimming")
    class MessageWhitespaceTrimming {

        @Test
        @DisplayName("Should trim leading and trailing spaces from message")
        void shouldTrimMessageSpaces() {
            InstantMessage im = new InstantMessage("recipient", "   Hello World   ", 1);
            assertEquals("Hello World", im.message());
        }

        @Test
        @DisplayName("Should trim leading and trailing newlines from message")
        void shouldTrimMessageNewlines() {
            InstantMessage im = new InstantMessage("recipient", "\n\nHello World\n\n", 1);
            assertEquals("Hello World", im.message());
        }

        @Test
        @DisplayName("Should trim tabs from message")
        void shouldTrimMessageTabs() {
            InstantMessage im = new InstantMessage("recipient", "\t\tHello World\t\t", 1);
            assertEquals("Hello World", im.message());
        }

        @Test
        @DisplayName("Should trim mixed whitespace from message")
        void shouldTrimMixedWhitespace() {
            InstantMessage im = new InstantMessage("recipient", " \t\n Hello World \n\t ", 1);
            assertEquals("Hello World", im.message());
        }

        @Test
        @DisplayName("Should preserve internal whitespace in message")
        void shouldPreserveInternalWhitespace() {
            InstantMessage im = new InstantMessage("recipient", "Hello   World\n\nGoodbye", 1);
            assertEquals("Hello   World\n\nGoodbye", im.message());
        }

        @Test
        @DisplayName("Should handle whitespace-only message")
        void shouldHandleWhitespaceOnlyMessage() {
            InstantMessage im = new InstantMessage("recipient", "   \t\n   ", 1);
            assertEquals("", im.message());
        }

        @Test
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            InstantMessage im = new InstantMessage("recipient", null, 1);
            assertNull(im.message());
        }

        @Test
        @DisplayName("Should handle empty message")
        void shouldHandleEmptyMessage() {
            InstantMessage im = new InstantMessage("recipient", "", 1);
            assertEquals("", im.message());
        }
    }

    @Nested
    @DisplayName("Recipient whitespace trimming")
    class RecipientWhitespaceTrimming {

        @Test
        @DisplayName("Should trim leading and trailing spaces from recipient")
        void shouldTrimRecipientSpaces() {
            InstantMessage im = new InstantMessage("   TestUser   ", "Hello", 1);
            assertEquals("TestUser", im.recipient());
        }

        @Test
        @DisplayName("Should trim newlines from recipient")
        void shouldTrimRecipientNewlines() {
            InstantMessage im = new InstantMessage("\nTestUser\n", "Hello", 1);
            assertEquals("TestUser", im.recipient());
        }

        @Test
        @DisplayName("Should trim tabs from recipient")
        void shouldTrimRecipientTabs() {
            InstantMessage im = new InstantMessage("\tTestUser\t", "Hello", 1);
            assertEquals("TestUser", im.recipient());
        }

        @Test
        @DisplayName("Should handle null recipient")
        void shouldHandleNullRecipient() {
            InstantMessage im = new InstantMessage(null, "Hello", 1);
            assertNull(im.recipient());
        }

        @Test
        @DisplayName("Should handle whitespace-only recipient")
        void shouldHandleWhitespaceOnlyRecipient() {
            InstantMessage im = new InstantMessage("   \t\n   ", "Hello", 1);
            assertEquals("", im.recipient());
        }
    }

    @Nested
    @DisplayName("Response ID handling")
    class ResponseIdHandling {

        @Test
        @DisplayName("Should preserve response ID")
        void shouldPreserveResponseId() {
            InstantMessage im = new InstantMessage("recipient", "Hello", 42);
            assertEquals(42, im.responseId());
        }

        @Test
        @DisplayName("Should allow null response ID")
        void shouldAllowNullResponseId() {
            InstantMessage im = new InstantMessage("recipient", "Hello", null);
            assertNull(im.responseId());
        }
    }

    @Nested
    @DisplayName("AOL line terminator handling")
    class AolLineTerminatorHandling {

        private static final char AOL_DEL = '\u007F';

        @Test
        @DisplayName("Should strip trailing AOL line terminators")
        void shouldStripTrailingAolTerminators() {
            InstantMessage im = new InstantMessage("recipient", "Hello" + AOL_DEL + AOL_DEL, 1);
            assertEquals("Hello", im.message());
        }

        @Test
        @DisplayName("Should strip leading AOL line terminators")
        void shouldStripLeadingAolTerminators() {
            InstantMessage im = new InstantMessage("recipient", "" + AOL_DEL + AOL_DEL + "Hello", 1);
            assertEquals("Hello", im.message());
        }

        @Test
        @DisplayName("Should strip mixed whitespace and AOL terminators")
        void shouldStripMixedWhitespaceAndAol() {
            InstantMessage im = new InstantMessage("recipient", "  \n" + AOL_DEL + "Hello" + AOL_DEL + "\n  ", 1);
            assertEquals("Hello", im.message());
        }

        @Test
        @DisplayName("Should preserve internal AOL terminators")
        void shouldPreserveInternalAolTerminators() {
            String msgWithInternalDel = "Line1" + AOL_DEL + "Line2";
            InstantMessage im = new InstantMessage("recipient", msgWithInternalDel, 1);
            assertEquals(msgWithInternalDel, im.message());
        }

        @Test
        @DisplayName("Should handle only AOL terminators")
        void shouldHandleOnlyAolTerminators() {
            InstantMessage im = new InstantMessage("recipient", "" + AOL_DEL + AOL_DEL + AOL_DEL, 1);
            assertEquals("", im.message());
        }
    }

    @Nested
    @DisplayName("Real-world scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Should handle message with only trailing newlines (common paste scenario)")
        void shouldHandleTrailingNewlines() {
            // User copies text from somewhere, often has trailing newlines
            InstantMessage im = new InstantMessage("recipient", "Check out this link\n\n\n", 1);
            assertEquals("Check out this link", im.message());
        }

        @Test
        @DisplayName("Should handle message with leading newlines (common paste scenario)")
        void shouldHandleLeadingNewlines() {
            InstantMessage im = new InstantMessage("recipient", "\n\n\nHere's my message", 1);
            assertEquals("Here's my message", im.message());
        }

        @Test
        @DisplayName("Should handle mixed content with HTML-style spacing")
        void shouldHandleMixedContent() {
            // Some clients might send formatted content
            InstantMessage im = new InstantMessage("recipient", "  <b>Bold</b>  ", 1);
            assertEquals("<b>Bold</b>", im.message());
        }

        @Test
        @DisplayName("Should preserve intentional line breaks in multiline message")
        void shouldPreserveIntentionalLineBreaks() {
            String multiline = "Line 1\nLine 2\nLine 3";
            InstantMessage im = new InstantMessage("recipient", "  " + multiline + "  ", 1);
            assertEquals(multiline, im.message());
        }
    }
}
