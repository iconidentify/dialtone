/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.im.IMTokenBehavior;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IM token handling behavior configuration.
 *
 * Tests the IMTokenBehavior record that encapsulates differences between iS and iT tokens,
 * validating that the behavioral configuration matches expected protocol behavior.
 *
 * <p>Protocol behavior summary:
 * <ul>
 *   <li>iS: Sends ACK response (via AckIsFdoBuilder), NO echo to sender</li>
 *   <li>iT: Sends noop response (via NoopFdoBuilder), echoes message back to sender</li>
 * </ul>
 */
@DisplayName("IM Token Handler Tests")
class IMTokenHandlerTest {

    @Nested
    @DisplayName("IMTokenBehavior Configuration")
    class IMTokenBehaviorTests {

        @Test
        @DisplayName("iS behavior should have correct token name")
        void isBehaviorShouldHaveCorrectTokenName() {
            assertEquals("iS", IMTokenBehavior.IS.tokenName());
        }

        @Test
        @DisplayName("iT behavior should have correct token name")
        void itBehaviorShouldHaveCorrectTokenName() {
            assertEquals("iT", IMTokenBehavior.IT.tokenName());
        }

        @Test
        @DisplayName("iS behavior should have IS_ACK log label")
        void isBehaviorShouldHaveAckLogLabel() {
            assertEquals("IS_ACK", IMTokenBehavior.IS.logLabel());
        }

        @Test
        @DisplayName("iT behavior should have IS_NOOP log label")
        void itBehaviorShouldHaveNoopLogLabel() {
            assertEquals("IS_NOOP", IMTokenBehavior.IT.logLabel());
        }

        @Test
        @DisplayName("iS behavior should NOT echo to sender")
        void isBehaviorShouldNotEchoToSender() {
            assertFalse(IMTokenBehavior.IS.echoToSender());
        }

        @Test
        @DisplayName("iT behavior SHOULD echo to sender")
        void itBehaviorShouldEchoToSender() {
            assertTrue(IMTokenBehavior.IT.echoToSender());
        }

        @Test
        @DisplayName("iS and iT behaviors should be different instances")
        void behaviorsShouldbeDifferentInstances() {
            assertNotEquals(IMTokenBehavior.IS, IMTokenBehavior.IT);
        }

        @Test
        @DisplayName("iS constant should be same reference on repeated access")
        void isConstantShouldBeSameReference() {
            assertSame(IMTokenBehavior.IS, IMTokenBehavior.IS);
        }

        @Test
        @DisplayName("iT constant should be same reference on repeated access")
        void itConstantShouldBeSameReference() {
            assertSame(IMTokenBehavior.IT, IMTokenBehavior.IT);
        }
    }

    @Nested
    @DisplayName("IMTokenBehavior Record Semantics")
    class RecordSemanticsTests {

        @Test
        @DisplayName("Custom IMTokenBehavior should work correctly")
        void customBehaviorShouldWork() {
            IMTokenBehavior custom = new IMTokenBehavior("iX", "CUSTOM_LABEL", true);

            assertEquals("iX", custom.tokenName());
            assertEquals("CUSTOM_LABEL", custom.logLabel());
            assertTrue(custom.echoToSender());
        }

        @Test
        @DisplayName("Record equality should work correctly")
        void recordEqualityShouldWork() {
            IMTokenBehavior a = new IMTokenBehavior("iS", "IS_ACK", false);
            IMTokenBehavior b = new IMTokenBehavior("iS", "IS_ACK", false);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Record toString should contain field values")
        void recordToStringShouldContainFields() {
            String str = IMTokenBehavior.IS.toString();

            assertTrue(str.contains("iS"));
            assertTrue(str.contains("IS_ACK"));
        }
    }

    @Nested
    @DisplayName("Protocol Behavior Validation")
    class ProtocolBehaviorTests {

        /**
         * These tests document the expected protocol behavior.
         * The actual handler implementation should match these expectations.
         * Response FDO is generated via DSL builders (AckIsFdoBuilder, NoopFdoBuilder).
         */

        @Test
        @DisplayName("iS should use IS_ACK log label for ACK response")
        void isShouldUseAckLogLabel() {
            // iS protocol: sender expects ACK confirmation
            // AckIsFdoBuilder generates the ACK FDO
            assertEquals("IS_ACK", IMTokenBehavior.IS.logLabel());
        }

        @Test
        @DisplayName("iT should use IS_NOOP log label for noop response")
        void itShouldUseNoopLogLabel() {
            // iT protocol: no ACK needed, NoopFdoBuilder generates noop FDO
            assertEquals("IS_NOOP", IMTokenBehavior.IT.logLabel());
        }

        @Test
        @DisplayName("Only iT should echo message back to sender")
        void onlyItShouldEcho() {
            // Design: iT echoes so sender sees their own message in chat window
            // iS relies on ACK for confirmation, no echo needed
            assertFalse(IMTokenBehavior.IS.echoToSender(), "iS should NOT echo");
            assertTrue(IMTokenBehavior.IT.echoToSender(), "iT SHOULD echo");
        }
    }
}
