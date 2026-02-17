/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.ImageViewerConfig;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ImageViewerFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageViewerFdoBuilder")
class ImageViewerFdoBuilderTest {

    private static final String TEST_ART_ID = "32-5446";
    private static final String TEST_TITLE = "Test Image";
    private static final int TEST_WIDTH = 400;
    private static final int TEST_HEIGHT = 300;
    private static final int TEST_X = 20;
    private static final int TEST_Y = 20;

    private ImageViewerConfig config;
    private ImageViewerFdoBuilder builder;

    @BeforeEach
    void setUp() {
        config = new ImageViewerConfig(TEST_ART_ID, TEST_TITLE, TEST_WIDTH, TEST_HEIGHT, TEST_X, TEST_Y);
        builder = new ImageViewerFdoBuilder(config);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("image-viewer", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
            assertTrue(description.toLowerCase().contains("image"),
                "Description should mention image");
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should throw exception for null config")
        void shouldThrowExceptionForNullConfig() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerFdoBuilder(null));
        }

        @Test
        @DisplayName("Should accept valid config")
        void shouldAcceptValidConfig() {
            assertDoesNotThrow(() -> new ImageViewerFdoBuilder(config));
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
        @DisplayName("Should contain window title")
        void shouldContainWindowTitle() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains(TEST_TITLE), "Should contain window title");
        }

        @Test
        @DisplayName("Should contain ind_group object")
        void shouldContainIndGroupObject() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("ind_group"), "Should have ind_group object");
        }

        @Test
        @DisplayName("Should contain orientation vcf")
        void shouldContainOrientationVcf() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_orientation") || fdo.contains("orientation"),
                "Should have orientation");
            assertTrue(fdo.toLowerCase().contains("vcf"), "Should use VCF orientation");
        }

        @Test
        @DisplayName("Should contain centered position")
        void shouldContainCenteredPosition() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("center_center") || fdo.contains("CENTER_CENTER"),
                "Should be centered");
        }

        @Test
        @DisplayName("Should contain precise dimensions")
        void shouldContainPreciseDimensions() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_precise_width") || fdo.contains("PRECISE_WIDTH"),
                "Should have precise width");
            assertTrue(fdo.contains("mat_precise_height") || fdo.contains("PRECISE_HEIGHT"),
                "Should have precise height");
            assertTrue(fdo.contains(String.valueOf(TEST_WIDTH)), "Should contain width value");
            assertTrue(fdo.contains(String.valueOf(TEST_HEIGHT)), "Should contain height value");
        }

        @Test
        @DisplayName("Should contain bool_precise yes")
        void shouldContainBoolPreciseYes() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_bool_precise") || fdo.contains("BOOL_PRECISE"),
                "Should have bool_precise");
        }

        @Test
        @DisplayName("Should contain modal no")
        void shouldContainModalNo() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_bool_modal") || fdo.contains("BOOL_MODAL"),
                "Should have bool_modal");
        }

        @Test
        @DisplayName("Should contain background tile")
        void shouldContainBackgroundTile() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_bool_background_tile") || fdo.contains("background_tile"),
                "Should have background tile");
        }

        @Test
        @DisplayName("Should contain background art ID")
        void shouldContainBackgroundArtId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_art_id") || fdo.contains("art_id"),
                "Should have art_id");
            assertTrue(fdo.contains("27256") || fdo.contains("1, 69, 27256"),
                "Should reference background art GID 1-69-27256");
        }

        @Test
        @DisplayName("Should contain ornament object")
        void shouldContainOrnamentObject() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("ornament"), "Should have ornament object");
        }

        @Test
        @DisplayName("Should contain image position")
        void shouldContainImagePosition() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_precise_x") || fdo.contains("PRECISE_X"),
                "Should have precise_x");
            assertTrue(fdo.contains("mat_precise_y") || fdo.contains("PRECISE_Y"),
                "Should have precise_y");
        }

        @Test
        @DisplayName("Should contain frame style")
        void shouldContainFrameStyle() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_frame_style") || fdo.contains("FRAME_STYLE"),
                "Should have frame_style");
            assertTrue(fdo.contains("5"), "Should have frame style 5");
        }

        @Test
        @DisplayName("Should contain image art ID")
        void shouldContainImageArtId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // The art ID "32-5446" should appear in some form
            assertTrue(fdo.contains("32") && fdo.contains("5446"),
                "Should contain image art ID components");
        }

        @Test
        @DisplayName("Should contain update display")
        void shouldContainUpdateDisplay() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_update_display") || fdo.contains("update_display"),
                "Should have update display");
        }

        @Test
        @DisplayName("Should contain wait off")
        void shouldContainWaitOff() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_wait_off"), "Should have wait off");
        }
    }

    @Nested
    @DisplayName("Three-part art ID")
    class ThreePartArtId {

        @Test
        @DisplayName("Should handle three-part art ID")
        void shouldHandleThreePartArtId() {
            ImageViewerConfig threePartConfig = new ImageViewerConfig(
                "1-0-21029", TEST_TITLE, TEST_WIDTH, TEST_HEIGHT, TEST_X, TEST_Y);
            ImageViewerFdoBuilder threePartBuilder = new ImageViewerFdoBuilder(threePartConfig);

            String fdo = threePartBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("1") && fdo.contains("0") && fdo.contains("21029"),
                "Should contain all parts of art ID");
        }
    }

    @Nested
    @DisplayName("Structural order")
    class StructuralOrder {

        @Test
        @DisplayName("Should have correct structural order")
        void shouldHaveCorrectStructuralOrder() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            int streamStart = fdo.indexOf("uni_start_stream");
            int indGroup = fdo.indexOf("ind_group");
            int ornament = fdo.indexOf("ornament");
            int updateDisplay = fdo.indexOf("man_update_display");
            int waitOff = fdo.indexOf("uni_wait_off");
            int streamEnd = fdo.lastIndexOf("uni_end_stream");

            assertTrue(streamStart < indGroup, "Stream start before ind_group");
            assertTrue(indGroup < ornament, "ind_group before ornament");
            assertTrue(ornament < updateDisplay, "ornament before update display");
            assertTrue(updateDisplay < waitOff, "update display before wait off");
            assertTrue(waitOff < streamEnd, "wait off before stream end");
        }
    }

    @Nested
    @DisplayName("Platform variants")
    class PlatformVariants {

        @Test
        @DisplayName("All platform combinations should produce valid FDO")
        void allPlatformCombinationsShouldProduceValidFdo() {
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, false)),
                "Mac color");
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, true)),
                "Mac BW");
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false)),
                "Windows color");
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, true)),
                "Windows BW");
        }

        @Test
        @DisplayName("All contexts should produce same output (no BW variant)")
        void allContextsShouldProduceSameOutput() {
            RenderingContext colorCtx = new RenderingContext(ClientPlatform.WINDOWS, false);
            RenderingContext bwCtx = new RenderingContext(ClientPlatform.WINDOWS, true);

            String colorFdo = builder.toSource(colorCtx);
            String bwFdo = builder.toSource(bwCtx);

            // Image viewer has no BW variant, so output should be identical
            assertEquals(colorFdo, bwFdo, "Color and BW should produce same output");
        }

        private void assertValidFdo(String fdo, String description) {
            assertNotNull(fdo, description + " should not be null");
            assertFalse(fdo.isEmpty(), description + " should not be empty");
            assertTrue(fdo.contains("uni_start_stream"), description + " should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), description + " should have stream end");
            assertTrue(fdo.contains("ind_group"), description + " should have ind_group");
            assertTrue(fdo.contains("ornament"), description + " should have ornament");
        }
    }

    @Nested
    @DisplayName("Consistency")
    class Consistency {

        @Test
        @DisplayName("Should produce consistent output on multiple calls")
        void shouldProduceConsistentOutput() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo1 = builder.toSource(ctx);
            String fdo2 = builder.toSource(ctx);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("forConfig should create builder with config")
        void forConfigShouldCreateBuilder() {
            ImageViewerFdoBuilder factoryBuilder = ImageViewerFdoBuilder.forConfig(config);
            String fdo = factoryBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
            assertTrue(fdo.contains(TEST_TITLE));
        }
    }

    @Nested
    @DisplayName("Dynamic configuration")
    class DynamicConfiguration {

        @Test
        @DisplayName("Should include custom dimensions in output")
        void shouldIncludeCustomDimensions() {
            ImageViewerConfig customConfig = new ImageViewerConfig(
                TEST_ART_ID, TEST_TITLE, 800, 600, 50, 75);
            ImageViewerFdoBuilder customBuilder = new ImageViewerFdoBuilder(customConfig);

            String fdo = customBuilder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("800"), "Should contain custom width");
            assertTrue(fdo.contains("600"), "Should contain custom height");
            assertTrue(fdo.contains("50"), "Should contain custom X position");
            assertTrue(fdo.contains("75"), "Should contain custom Y position");
        }

        @Test
        @DisplayName("Should include custom title in output")
        void shouldIncludeCustomTitle() {
            ImageViewerConfig customConfig = new ImageViewerConfig(
                TEST_ART_ID, "Custom Title", TEST_WIDTH, TEST_HEIGHT, TEST_X, TEST_Y);
            ImageViewerFdoBuilder customBuilder = new ImageViewerFdoBuilder(customConfig);

            String fdo = customBuilder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("Custom Title"), "Should contain custom title");
        }
    }
}
