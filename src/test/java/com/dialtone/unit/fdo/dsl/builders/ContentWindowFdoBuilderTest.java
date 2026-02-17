/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.ContentWindowConfig;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ContentWindowFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentWindowFdoBuilder")
class ContentWindowFdoBuilderTest {

    private static final String TEST_CONTENT = "Test content for the window.\r\rPlease read carefully.";
    private ContentWindowConfig testConfig;
    private ContentWindowFdoBuilder builder;

    @BeforeEach
    void setUp() {
        testConfig = new ContentWindowConfig(
            "test",
            "Test Window",
            "1-69-27256",
            "1-69-40001",
            518,
            300,
            null  // No button theme needed (no Close button)
        );
        builder = new ContentWindowFdoBuilder(testConfig, TEST_CONTENT);
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should create builder with valid config and content")
        void shouldCreateWithValidParams() {
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(testConfig, TEST_CONTENT);
            assertNotNull(b);
            assertEquals(testConfig, b.getConfig());
            assertEquals(TEST_CONTENT, b.getContent());
        }

        @Test
        @DisplayName("Should create builder with config only")
        void shouldCreateWithConfigOnly() {
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(testConfig);
            assertNotNull(b);
            assertEquals("", b.getContent());
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowFdoBuilder(null, TEST_CONTENT)
            );
        }

        @Test
        @DisplayName("Should handle null content")
        void shouldHandleNullContent() {
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(testConfig, null);
            assertEquals("", b.getContent());
        }
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return keyword as GID")
        void shouldReturnKeywordAsGid() {
            assertEquals("test", builder.getGid());
        }

        @Test
        @DisplayName("Should return description containing window title")
        void shouldReturnDescriptionWithTitle() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertTrue(description.contains("Test Window"));
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
        @DisplayName("Should contain window title")
        void shouldContainWindowTitle() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("Test Window") || fdo.contains("mat_title"),
                "Should have window title");
        }

        @Test
        @DisplayName("Should contain centered positioning")
        void shouldContainPosition() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("center_center") || fdo.contains("CENTER_CENTER"),
                "Should be centered");
        }

        @Test
        @DisplayName("Should contain background art when configured")
        void shouldContainBackgroundArt() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_art_id") || fdo.contains("art_id"),
                "Should have background art");
            assertTrue(fdo.contains("27256") || fdo.contains("1, 69, 27256"),
                "Should reference correct art GID");
        }

        @Test
        @DisplayName("Should contain logo art when configured")
        void shouldContainLogoArt() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("40001") || fdo.contains("1, 69, 40001"),
                "Should reference logo art GID");
        }

        @Test
        @DisplayName("Should not contain logo when not configured")
        void shouldNotContainLogoWhenNotConfigured() {
            ContentWindowConfig noLogoConfig = new ContentWindowConfig(
                "test", "Test", "1-69-27256", null, 518, 300, null
            );
            ContentWindowFdoBuilder noLogoBuilder = new ContentWindowFdoBuilder(noLogoConfig, "content");
            String fdo = noLogoBuilder.toSource(RenderingContext.DEFAULT);

            assertFalse(fdo.contains("30001"), "Should not have logo GID");
        }

        @Test
        @DisplayName("Should contain content view")
        void shouldContainContentView() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("view"), "Should have view widget");
        }

        @Test
        @DisplayName("Should contain precise width for pixel-based sizing")
        void shouldContainPreciseWidth() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_precise_width") || fdo.contains("518"),
                "Should have precise width");
        }

        @Test
        @DisplayName("Should contain precise height for pixel-based sizing")
        void shouldContainPreciseHeight() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_precise_height") || fdo.contains("300"),
                "Should have precise height");
        }

        @Test
        @DisplayName("Should contain bool_precise to lock size")
        void shouldContainBoolPrecise() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_bool_precise") || fdo.contains("BOOL_PRECISE"),
                "Should have bool_precise to lock exact size");
        }

        @Test
        @DisplayName("Should contain uni_wait_off at end")
        void shouldContainWaitOff() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_wait_off"), "Should have wait off");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo1 = builder.toSource(ctx);
            String fdo2 = builder.toSource(ctx);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }

        @Test
        @DisplayName("Should handle empty content")
        void shouldHandleEmptyContent() {
            ContentWindowFdoBuilder emptyBuilder = new ContentWindowFdoBuilder(testConfig, "");
            String fdo = emptyBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
            assertTrue(fdo.contains("uni_end_stream"));
        }

        @Test
        @DisplayName("Should handle no background art")
        void shouldHandleNoBackgroundArt() {
            ContentWindowConfig noBgConfig = new ContentWindowConfig(
                "test", "Test", null, null, 518, 300, null
            );
            ContentWindowFdoBuilder noBgBuilder = new ContentWindowFdoBuilder(noBgConfig, "content");
            String fdo = noBgBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
        }
    }

    @Nested
    @DisplayName("Platform-specific variants")
    class PlatformVariants {

        @Test
        @DisplayName("All four combinations should produce valid FDO")
        void allFourCombinationsShouldProduceValidFdo() {
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
        @DisplayName("Color and BW modes should differ (view size)")
        void colorAndBwModesShouldDiffer() {
            RenderingContext colorCtx = new RenderingContext(ClientPlatform.WINDOWS, false);
            RenderingContext bwCtx = new RenderingContext(ClientPlatform.WINDOWS, true);

            String colorFdo = builder.toSource(colorCtx);
            String bwFdo = builder.toSource(bwCtx);

            // View sizes differ between color (70x18) and BW (60x14) modes
            assertNotEquals(colorFdo, bwFdo, "Color and BW FDO should differ (view sizes)");
        }

        private void assertValidFdo(String fdo, String description) {
            assertNotNull(fdo, description + " should not be null");
            assertFalse(fdo.isEmpty(), description + " should not be empty");
            assertTrue(fdo.contains("uni_start_stream"), description + " should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), description + " should have stream end");
            assertTrue(fdo.contains("mat_precise_width") || fdo.contains("518"),
                description + " should have precise width");
        }
    }

    @Nested
    @DisplayName("Content handling")
    class ContentHandling {

        @Test
        @DisplayName("Should convert newlines to AOL format")
        void shouldConvertNewlines() {
            String contentWithNewlines = "Line 1\nLine 2\nLine 3";
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(testConfig, contentWithNewlines);
            String fdo = b.toSource(RenderingContext.DEFAULT);

            // Content should be present (newlines converted internally)
            assertNotNull(fdo);
            assertTrue(fdo.contains("man_append_data") || fdo.contains("Line 1"));
        }

        @Test
        @DisplayName("Should handle Windows-style newlines")
        void shouldHandleWindowsNewlines() {
            String contentWithCrlf = "Line 1\r\nLine 2\r\nLine 3";
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(testConfig, contentWithCrlf);
            String fdo = b.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }
    }

    @Nested
    @DisplayName("Art GID parsing")
    class ArtGidParsing {

        @Test
        @DisplayName("Should parse 2-part GID (domain-id)")
        void shouldParseTwoPartGid() {
            ContentWindowConfig twoPartConfig = new ContentWindowConfig(
                "test", "Test", "32-5447", null, 518, 300, null
            );
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(twoPartConfig, "content");
            String fdo = b.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("5447") || fdo.contains("32, 5447"),
                "Should parse 2-part GID");
        }

        @Test
        @DisplayName("Should parse 3-part GID (a-b-c)")
        void shouldParseThreePartGid() {
            ContentWindowConfig threePartConfig = new ContentWindowConfig(
                "test", "Test", "1-69-27256", null, 518, 300, null
            );
            ContentWindowFdoBuilder b = new ContentWindowFdoBuilder(threePartConfig, "content");
            String fdo = b.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("27256") || fdo.contains("1, 69, 27256"),
                "Should parse 3-part GID");
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
            int view = fdo.indexOf("view");
            int waitOff = fdo.indexOf("uni_wait_off");
            int streamEnd = fdo.lastIndexOf("uni_end_stream");

            assertTrue(streamStart < view, "Stream start before view");
            assertTrue(view < waitOff, "View before wait off");
            assertTrue(waitOff < streamEnd, "Wait off before stream end");
        }
    }
}
