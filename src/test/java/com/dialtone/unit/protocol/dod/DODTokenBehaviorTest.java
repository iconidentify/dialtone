/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.dod;

import com.dialtone.fdo.dsl.builders.F1DodFailedFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.dod.DODTokenBehavior;
import com.dialtone.protocol.dod.DODGidConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DOD token handling behavior configuration.
 *
 * Tests the DODTokenBehavior and DODGidConfig records that encapsulate
 * differences between f2, f1, and K1 tokens, validating that the behavioral
 * configuration matches expected protocol behavior.
 *
 * <p>Protocol behavior summary:
 * <ul>
 *   <li>f2: Binary GID at offset+2, sends ACK on error, no 'found' flag</li>
 *   <li>f1: Binary GID at offset+10, sends FDO template on error, has 'found' flag</li>
 *   <li>K1: FDO decompile for GID, sends noop FDO on error, has 'found' flag</li>
 * </ul>
 */
@DisplayName("DOD Token Behavior Tests")
class DODTokenBehaviorTest {

    @Nested
    @DisplayName("DODTokenBehavior Configuration")
    class DODTokenBehaviorTests {

        @Test
        @DisplayName("f2 behavior should have correct token name")
        void f2BehaviorShouldHaveCorrectTokenName() {
            assertEquals("f2", DODTokenBehavior.F2.tokenName());
        }

        @Test
        @DisplayName("f1 behavior should have correct token name")
        void f1BehaviorShouldHaveCorrectTokenName() {
            assertEquals("f1", DODTokenBehavior.F1.tokenName());
        }

        @Test
        @DisplayName("K1 behavior should have correct token name")
        void k1BehaviorShouldHaveCorrectTokenName() {
            assertEquals("K1", DODTokenBehavior.K1.tokenName());
        }

        @Test
        @DisplayName("f2 should use binary offset GID extraction")
        void f2ShouldUseBinaryOffsetExtraction() {
            assertEquals(DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.F2.gidExtraction());
        }

        @Test
        @DisplayName("f1 should use binary offset GID extraction")
        void f1ShouldUseBinaryOffsetExtraction() {
            assertEquals(DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.F1.gidExtraction());
        }

        @Test
        @DisplayName("K1 should use FDO decompile GID extraction")
        void k1ShouldUseFdoDecompileExtraction() {
            assertEquals(DODTokenBehavior.GidExtractionMethod.FDO_DECOMPILE,
                DODTokenBehavior.K1.gidExtraction());
        }

        @Test
        @DisplayName("f2 should use ACK error response")
        void f2ShouldUseAckErrorResponse() {
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_ACK,
                DODTokenBehavior.F2.errorResponse());
            assertNull(DODTokenBehavior.F2.errorBuilder());
        }

        @Test
        @DisplayName("f1 should use FDO template error response")
        void f1ShouldUseFdoTemplateErrorResponse() {
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE,
                DODTokenBehavior.F1.errorResponse());
            assertSame(F1DodFailedFdoBuilder.INSTANCE, DODTokenBehavior.F1.errorBuilder());
        }

        @Test
        @DisplayName("K1 should use noop FDO template error response")
        void k1ShouldUseNoopFdoTemplateErrorResponse() {
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE,
                DODTokenBehavior.K1.errorResponse());
            assertSame(NoopFdoBuilder.INSTANCE, DODTokenBehavior.K1.errorBuilder());
        }

        @Test
        @DisplayName("f2 should have correct success log label")
        void f2ShouldHaveCorrectSuccessLogLabel() {
            assertEquals("F2_DOD_RESPONSE", DODTokenBehavior.F2.successLogLabel());
        }

        @Test
        @DisplayName("f1 should have correct success log label")
        void f1ShouldHaveCorrectSuccessLogLabel() {
            assertEquals("F1_ATOM_RESPONSE", DODTokenBehavior.F1.successLogLabel());
        }

        @Test
        @DisplayName("K1 should have correct success log label")
        void k1ShouldHaveCorrectSuccessLogLabel() {
            assertEquals("K1_RESPONSE", DODTokenBehavior.K1.successLogLabel());
        }

        @Test
        @DisplayName("f2 should have correct empty log label")
        void f2ShouldHaveCorrectEmptyLogLabel() {
            assertEquals("F2_CTRL_ACK", DODTokenBehavior.F2.emptyLogLabel());
        }

        @Test
        @DisplayName("f1 should have correct empty log label")
        void f1ShouldHaveCorrectEmptyLogLabel() {
            assertEquals("F1_DOD_FAILED", DODTokenBehavior.F1.emptyLogLabel());
        }

        @Test
        @DisplayName("K1 should have correct empty log label")
        void k1ShouldHaveCorrectEmptyLogLabel() {
            assertEquals("K1_ACK", DODTokenBehavior.K1.emptyLogLabel());
        }

        @Test
        @DisplayName("f2 should NOT use found flag")
        void f2ShouldNotUseFoundFlag() {
            assertFalse(DODTokenBehavior.F2.usesFoundFlag());
        }

        @Test
        @DisplayName("f1 SHOULD use found flag")
        void f1ShouldUseFoundFlag() {
            assertTrue(DODTokenBehavior.F1.usesFoundFlag());
        }

        @Test
        @DisplayName("K1 SHOULD use found flag")
        void k1ShouldUseFoundFlag() {
            assertTrue(DODTokenBehavior.K1.usesFoundFlag());
        }
    }

    @Nested
    @DisplayName("DODGidConfig Configuration")
    class DODGidConfigTests {

        @Test
        @DisplayName("f2 config should have correct token bytes")
        void f2ConfigShouldHaveCorrectTokenBytes() {
            assertEquals((byte) 0x66, DODGidConfig.F2.tokenByte1()); // 'f'
            assertEquals((byte) 0x32, DODGidConfig.F2.tokenByte2()); // '2'
        }

        @Test
        @DisplayName("f1 config should have correct token bytes")
        void f1ConfigShouldHaveCorrectTokenBytes() {
            assertEquals((byte) 0x66, DODGidConfig.F1.tokenByte1()); // 'f'
            assertEquals((byte) 0x31, DODGidConfig.F1.tokenByte2()); // '1'
        }

        @Test
        @DisplayName("f2 GID offset should be 2")
        void f2GidOffsetShouldBe2() {
            assertEquals(2, DODGidConfig.F2.gidOffset());
        }

        @Test
        @DisplayName("f1 GID offset should be 10")
        void f1GidOffsetShouldBe10() {
            assertEquals(10, DODGidConfig.F1.gidOffset());
        }

        @Test
        @DisplayName("f2 min required bytes should be 6")
        void f2MinRequiredBytesShouldBe6() {
            assertEquals(6, DODGidConfig.F2.minRequiredBytes());
        }

        @Test
        @DisplayName("f1 min required bytes should be 14")
        void f1MinRequiredBytesShouldBe14() {
            assertEquals(14, DODGidConfig.F1.minRequiredBytes());
        }
    }

    @Nested
    @DisplayName("Record Semantics Tests")
    class RecordSemanticsTests {

        @Test
        @DisplayName("All behaviors should be different instances")
        void behaviorsShouldBeDifferentInstances() {
            assertNotEquals(DODTokenBehavior.F2, DODTokenBehavior.F1);
            assertNotEquals(DODTokenBehavior.F1, DODTokenBehavior.K1);
            assertNotEquals(DODTokenBehavior.F2, DODTokenBehavior.K1);
        }

        @Test
        @DisplayName("Behavior constants should be same reference on repeated access")
        void behaviorConstantsShouldBeSameReference() {
            assertSame(DODTokenBehavior.F2, DODTokenBehavior.F2);
            assertSame(DODTokenBehavior.F1, DODTokenBehavior.F1);
            assertSame(DODTokenBehavior.K1, DODTokenBehavior.K1);
        }

        @Test
        @DisplayName("GidConfig constants should be same reference on repeated access")
        void gidConfigConstantsShouldBeSameReference() {
            assertSame(DODGidConfig.F2, DODGidConfig.F2);
            assertSame(DODGidConfig.F1, DODGidConfig.F1);
        }

        @Test
        @DisplayName("Record toString should contain field values")
        void recordToStringShouldContainFields() {
            String str = DODTokenBehavior.F2.toString();
            assertTrue(str.contains("f2"));
            assertTrue(str.contains("F2_DOD_RESPONSE"));
        }

        @Test
        @DisplayName("Record equality should work correctly")
        void recordEqualityShouldWork() {
            DODTokenBehavior a = new DODTokenBehavior(
                "f2",
                DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.ErrorResponseType.SEND_ACK,
                null,
                "F2_DOD_RESPONSE",
                "F2_CTRL_ACK",
                false
            );
            DODTokenBehavior b = new DODTokenBehavior(
                "f2",
                DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.ErrorResponseType.SEND_ACK,
                null,
                "F2_DOD_RESPONSE",
                "F2_CTRL_ACK",
                false
            );

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Custom DODTokenBehavior should work correctly")
        void customBehaviorShouldWork() {
            DODTokenBehavior custom = new DODTokenBehavior(
                "fX",
                DODTokenBehavior.GidExtractionMethod.FDO_DECOMPILE,
                DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE,
                NoopFdoBuilder.INSTANCE,
                "CUSTOM_SUCCESS",
                "CUSTOM_EMPTY",
                true
            );

            assertEquals("fX", custom.tokenName());
            assertEquals(DODTokenBehavior.GidExtractionMethod.FDO_DECOMPILE, custom.gidExtraction());
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE, custom.errorResponse());
            assertSame(NoopFdoBuilder.INSTANCE, custom.errorBuilder());
            assertEquals("CUSTOM_SUCCESS", custom.successLogLabel());
            assertEquals("CUSTOM_EMPTY", custom.emptyLogLabel());
            assertTrue(custom.usesFoundFlag());
        }

        @Test
        @DisplayName("Custom DODGidConfig should work correctly")
        void customGidConfigShouldWork() {
            DODGidConfig custom = new DODGidConfig(
                (byte) 0x4B, // 'K'
                (byte) 0x31, // '1'
                5,
                10
            );

            assertEquals((byte) 0x4B, custom.tokenByte1());
            assertEquals((byte) 0x31, custom.tokenByte2());
            assertEquals(5, custom.gidOffset());
            assertEquals(10, custom.minRequiredBytes());
        }
    }

    @Nested
    @DisplayName("Protocol Behavior Validation")
    class ProtocolBehaviorTests {

        @Test
        @DisplayName("f2 and f1 should both use binary offset extraction")
        void f2AndF1ShouldUseBinaryOffset() {
            assertEquals(DODTokenBehavior.F2.gidExtraction(), DODTokenBehavior.F1.gidExtraction());
            assertEquals(DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.F2.gidExtraction());
        }

        @Test
        @DisplayName("Only f2 should use ACK error response")
        void onlyF2ShouldUseAckError() {
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_ACK,
                DODTokenBehavior.F2.errorResponse());
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE,
                DODTokenBehavior.F1.errorResponse());
            assertEquals(DODTokenBehavior.ErrorResponseType.SEND_FDO_TEMPLATE,
                DODTokenBehavior.K1.errorResponse());
        }

        @Test
        @DisplayName("Only K1 should use FDO decompile extraction")
        void onlyK1ShouldUseFdoDecompile() {
            assertEquals(DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.F2.gidExtraction());
            assertEquals(DODTokenBehavior.GidExtractionMethod.BINARY_OFFSET,
                DODTokenBehavior.F1.gidExtraction());
            assertEquals(DODTokenBehavior.GidExtractionMethod.FDO_DECOMPILE,
                DODTokenBehavior.K1.gidExtraction());
        }

        @Test
        @DisplayName("f1 and K1 should use found flag, f2 should not")
        void foundFlagShouldMatchResponseType() {
            assertFalse(DODTokenBehavior.F2.usesFoundFlag(), "f2 should NOT use found flag");
            assertTrue(DODTokenBehavior.F1.usesFoundFlag(), "f1 SHOULD use found flag");
            assertTrue(DODTokenBehavior.K1.usesFoundFlag(), "K1 SHOULD use found flag");
        }

        @Test
        @DisplayName("f1 GID offset should be larger than f2 due to frame structure")
        void f1GidOffsetShouldBeLargerThanF2() {
            // f2: token(2) + GID(4) = offset 2
            // f1: token(2) + streamId(2) + flags(4) + marker(2) + GID(4) = offset 10
            assertTrue(DODGidConfig.F1.gidOffset() > DODGidConfig.F2.gidOffset());
        }
    }
}
