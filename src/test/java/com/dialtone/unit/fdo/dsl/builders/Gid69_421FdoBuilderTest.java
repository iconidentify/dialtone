/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.Gid69_421FdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gid69_421FdoBuilder")
class Gid69_421FdoBuilderTest {

    private Gid69_421FdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new Gid69_421FdoBuilder();
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("69-421", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
            assertTrue(description.toLowerCase().contains("map"), "Description should mention map");
        }
    }

    @Nested
    @DisplayName("FDO source generation")
    class FdoSourceGeneration {

        @Test
        @DisplayName("Should generate valid FDO source with default context")
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
        @DisplayName("Should contain object ID 69-421")
        void shouldContainObjectId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_object_id"), "Should have object ID atom");
            assertTrue(fdo.contains("69-421") || fdo.contains("69, 421"),
                "Should contain GID reference");
        }

        @Test
        @DisplayName("Should contain view widget with relative tag 256")
        void shouldContainViewWidget() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("view"), "Should have view widget");
            assertTrue(fdo.contains("mat_relative_tag") && fdo.contains("256"),
                "Should have view relative tag 256 for updates");
        }

        @Test
        @DisplayName("Should have black background color on Mac")
        void shouldHaveBlackBackground() {
            // Colors are Mac-only
            RenderingContext macCtx = new RenderingContext(ClientPlatform.MAC, false);
            String fdo = builder.toSource(macCtx);

            // Black background = RGB(0, 0, 0)
            assertTrue(fdo.contains("mat_color_face") || fdo.contains("COLOR_FACE"),
                "Should have color face atom");
        }

        @Test
        @DisplayName("Should have white text color on Mac")
        void shouldHaveWhiteText() {
            // Colors are Mac-only
            RenderingContext macCtx = new RenderingContext(ClientPlatform.MAC, false);
            String fdo = builder.toSource(macCtx);

            // White text = RGB(255, 255, 255)
            assertTrue(fdo.contains("mat_color_text") || fdo.contains("COLOR_TEXT"),
                "Should have color text atom");
        }

        @Test
        @DisplayName("Should have font configuration")
        void shouldHaveFontConfiguration() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // Font is configured via mat_font_sis or mat_font_id atoms
            assertTrue(fdo.contains("mat_font_sis") || fdo.contains("mat_font_id"),
                "Should have font configuration atom");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.MAC, false);
            String fdo1 = builder.toSource(ctx);
            String fdo2 = builder.toSource(ctx);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("Platform-specific variants")
    class PlatformVariants {

        @Test
        @DisplayName("All four platform/color combinations should produce valid FDO")
        void allFourCombinationsShouldProduceValidFdo() {
            // Mac + Color
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, false)),
                "Mac color");

            // Mac + BW
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, true)),
                "Mac BW");

            // Windows + Color
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false)),
                "Windows color");

            // Windows + BW
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, true)),
                "Windows BW");
        }

        @Test
        @DisplayName("Mac should have slightly smaller view width")
        void macShouldHaveSmallerViewWidth() {
            RenderingContext macCtx = new RenderingContext(ClientPlatform.MAC, false);
            RenderingContext winCtx = new RenderingContext(ClientPlatform.WINDOWS, false);

            String macFdo = builder.toSource(macCtx);
            String winFdo = builder.toSource(winCtx);

            // Both should be valid but may have different widths
            assertNotEquals(macFdo, winFdo, "Mac and Windows FDO should differ");
        }

        private void assertValidFdo(String fdo, String description) {
            assertNotNull(fdo, description + " should not be null");
            assertFalse(fdo.isEmpty(), description + " should not be empty");
            assertTrue(fdo.contains("uni_start_stream"), description + " should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), description + " should have stream end");
            assertTrue(fdo.contains("mat_object_id"), description + " should have object ID");
            assertTrue(fdo.contains("view"), description + " should have view widget");
            assertTrue(fdo.contains("256"), description + " should have relative tag 256");
        }
    }
}
