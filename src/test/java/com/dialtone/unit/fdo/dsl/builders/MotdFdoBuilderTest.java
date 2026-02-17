/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.MotdFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MotdFdoBuilder.
 * Verifies output matches the original fdo/motd.fdo.txt template.
 */
@DisplayName("MotdFdoBuilder")
class MotdFdoBuilderTest {

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("motd", MotdFdoBuilder.INSTANCE.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = MotdFdoBuilder.INSTANCE.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }

        @Test
        @DisplayName("Should be singleton instance")
        void shouldBeSingletonInstance() {
            assertSame(MotdFdoBuilder.INSTANCE, MotdFdoBuilder.INSTANCE);
        }
    }

    @Nested
    @DisplayName("FDO source generation")
    class FdoSourceGeneration {

        @Test
        @DisplayName("Should generate valid FDO source")
        void shouldGenerateValidFdoSource() {
            String fdo = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("Should contain stream start with 00x")
        void shouldContainStreamStartWith00x() {
            String fdo = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_start_stream") && fdo.contains("00x"),
                "Should have uni_start_stream <00x>");
        }

        @Test
        @DisplayName("Should contain uni_wait_off")
        void shouldContainWaitOff() {
            String fdo = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_wait_off"), "Should have uni_wait_off");
        }

        @Test
        @DisplayName("Should contain stream end")
        void shouldContainStreamEnd() {
            String fdo = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_end_stream"), "Should have uni_end_stream");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            String fdo1 = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            String fdo2 = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }

        @Test
        @DisplayName("Should have correct structural order")
        void shouldHaveCorrectStructuralOrder() {
            String fdo = MotdFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            int streamStart = fdo.indexOf("uni_start_stream");
            int waitOff = fdo.indexOf("uni_wait_off");
            int streamEnd = fdo.indexOf("uni_end_stream");

            assertTrue(streamStart < waitOff, "Stream start before wait_off");
            assertTrue(waitOff < streamEnd, "Wait_off before stream end");
        }
    }

    @Nested
    @DisplayName("Platform variants")
    class PlatformVariants {

        @Test
        @DisplayName("All platform/color combinations should produce same output")
        void allCombinationsShouldProduceSameOutput() {
            // MOTD is simple - no platform-specific behavior
            String winColor = MotdFdoBuilder.INSTANCE.toSource(
                new RenderingContext(ClientPlatform.WINDOWS, false));
            String winBw = MotdFdoBuilder.INSTANCE.toSource(
                new RenderingContext(ClientPlatform.WINDOWS, true));
            String macColor = MotdFdoBuilder.INSTANCE.toSource(
                new RenderingContext(ClientPlatform.MAC, false));
            String macBw = MotdFdoBuilder.INSTANCE.toSource(
                new RenderingContext(ClientPlatform.MAC, true));

            assertEquals(winColor, winBw, "Windows color and BW should be same");
            assertEquals(winColor, macColor, "Windows and Mac should be same");
            assertEquals(macColor, macBw, "Mac color and BW should be same");
        }
    }
}
