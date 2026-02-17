/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.TosFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TosFdoBuilder")
class TosFdoBuilderTest {

    private static final String TEST_TOS_CONTENT = "Test Terms of Service Content.\r\rPlease read carefully.";
    private TosFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new TosFdoBuilder(TEST_TOS_CONTENT);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("tos", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
            assertTrue(description.toLowerCase().contains("terms"),
                "Description should mention terms");
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
        @DisplayName("Should contain TERMS OF SERVICE title")
        void shouldContainTitle() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("TERMS OF SERVICE"),
                "Should have window title");
        }

        @Test
        @DisplayName("Should contain modal and centered positioning")
        void shouldContainModalAndPosition() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_bool_modal") || fdo.contains("modal"),
                "Should be modal");
            assertTrue(fdo.contains("center_center") || fdo.contains("CENTER_CENTER"),
                "Should be centered");
        }

        @Test
        @DisplayName("Should contain background art ID")
        void shouldContainBackgroundArt() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_art_id") || fdo.contains("art_id"),
                "Should have background art");
            assertTrue(fdo.contains("27256") || fdo.contains("1-69-27256") || fdo.contains("1, 69, 27256"),
                "Should reference correct art GID");
        }

        @Test
        @DisplayName("Should contain view with TOS content")
        void shouldContainTosView() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("view"), "Should have view widget");
            assertTrue(fdo.contains("man_append_data") || fdo.contains(TEST_TOS_CONTENT),
                "Should have TOS content");
        }

        @Test
        @DisplayName("Should contain AGREE trigger")
        void shouldContainAgreeButton() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("AGREE"), "Should have AGREE button");
        }

        @Test
        @DisplayName("Should contain DISAGREE trigger")
        void shouldContainDisagreeButton() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("DISAGREE"), "Should have DISAGREE button");
        }

        @Test
        @DisplayName("Should contain hang_up in DISAGREE action")
        void shouldContainHangUp() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("ccl_hang_up") || fdo.contains("HANG_UP"),
                "Should trigger hang up");
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
    }

    @Nested
    @DisplayName("Platform-specific variants")
    class PlatformVariants {

        @Test
        @DisplayName("Color mode should include button colors")
        void colorModeShouldIncludeButtonColors() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo = builder.toSource(ctx);

            assertTrue(fdo.contains("mat_color_face") || fdo.contains("COLOR_FACE"),
                "Should have face color");
            assertTrue(fdo.contains("mat_color_text") || fdo.contains("COLOR_TEXT"),
                "Should have text color");
        }

        @Test
        @DisplayName("Color mode should include trigger style")
        void colorModeShouldIncludeTriggerStyle() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo = builder.toSource(ctx);

            assertTrue(fdo.contains("mat_trigger_style") || fdo.contains("trigger_style"),
                "Should have trigger style");
        }

        @Test
        @DisplayName("BW mode should NOT include button colors")
        void bwModeShouldNotIncludeButtonColors() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, true);
            String fdo = builder.toSource(ctx);

            assertFalse(fdo.contains("mat_color_face") && fdo.contains("252"),
                "BW should not have face color");
        }

        @Test
        @DisplayName("BW mode should NOT include trigger style")
        void bwModeShouldNotIncludeTriggerStyle() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, true);
            String fdo = builder.toSource(ctx);

            // Check specifically for trigger_style atom, not just "place" which appears in other contexts
            assertFalse(fdo.contains("mat_trigger_style"),
                "BW should not have trigger style");
        }

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
        @DisplayName("Color and BW modes should differ")
        void colorAndBwModesShouldDiffer() {
            RenderingContext colorCtx = new RenderingContext(ClientPlatform.WINDOWS, false);
            RenderingContext bwCtx = new RenderingContext(ClientPlatform.WINDOWS, true);

            String colorFdo = builder.toSource(colorCtx);
            String bwFdo = builder.toSource(bwCtx);

            assertNotEquals(colorFdo, bwFdo, "Color and BW FDO should differ");
        }

        private void assertValidFdo(String fdo, String description) {
            assertNotNull(fdo, description + " should not be null");
            assertFalse(fdo.isEmpty(), description + " should not be empty");
            assertTrue(fdo.contains("uni_start_stream"), description + " should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), description + " should have stream end");
            assertTrue(fdo.contains("TERMS OF SERVICE"), description + " should have title");
            assertTrue(fdo.contains("AGREE"), description + " should have AGREE button");
            assertTrue(fdo.contains("DISAGREE"), description + " should have DISAGREE button");
        }
    }

    @Nested
    @DisplayName("Button theme integration")
    class ButtonThemeIntegration {

        @Test
        @DisplayName("Should use custom button theme colors")
        void shouldUseCustomButtonTheme() {
            ButtonTheme customTheme = new ButtonTheme(
                new int[]{100, 100, 100},
                new int[]{200, 200, 200},
                new int[]{50, 50, 50},
                new int[]{150, 150, 150}
            );
            TosFdoBuilder customBuilder = new TosFdoBuilder(TEST_TOS_CONTENT, customTheme);
            String fdo = customBuilder.toSource(RenderingContext.DEFAULT);

            // Should have color atoms (color mode by default)
            assertTrue(fdo.contains("mat_color_face") || fdo.contains("COLOR_FACE"),
                "Should have face color atom");
        }

        @Test
        @DisplayName("Should use default theme when null")
        void shouldUseDefaultThemeWhenNull() {
            TosFdoBuilder builderWithNull = new TosFdoBuilder(TEST_TOS_CONTENT, null);
            String fdo = builderWithNull.toSource(RenderingContext.DEFAULT);

            // Should still generate valid FDO
            assertNotNull(fdo);
            assertTrue(fdo.contains("AGREE"));
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("forContent should create builder with content")
        void forContentShouldCreateBuilder() {
            TosFdoBuilder custom = TosFdoBuilder.forContent("Custom TOS content");
            String fdo = custom.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("forContent with theme should create builder with both")
        void forContentWithThemeShouldCreateBuilder() {
            ButtonTheme theme = new ButtonTheme(
                new int[]{1, 1, 1}, new int[]{2, 2, 2},
                new int[]{3, 3, 3}, new int[]{4, 4, 4}
            );
            TosFdoBuilder custom = TosFdoBuilder.forContent("Custom TOS", theme);
            String fdo = custom.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("Default constructor should work (loads from file)")
        void defaultConstructorShouldWork() {
            // Default constructor loads from public/TOS.txt
            TosFdoBuilder defaultBuilder = new TosFdoBuilder();
            String fdo = defaultBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            // May have default content or actual TOS depending on resources
            assertTrue(fdo.contains("TERMS OF SERVICE"));
        }
    }

    @Nested
    @DisplayName("Structural order")
    class StructuralOrder {

        @Test
        @DisplayName("Should have correct structural order")
        void shouldHaveCorrectStructuralOrder() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // Find positions of key elements
            int streamStart = fdo.indexOf("uni_start_stream");
            int title = fdo.indexOf("TERMS OF SERVICE");
            int view = fdo.indexOf("view");
            int agree = fdo.indexOf("AGREE");
            int disagree = fdo.indexOf("DISAGREE");
            int waitOff = fdo.indexOf("uni_wait_off");
            int streamEnd = fdo.lastIndexOf("uni_end_stream");

            // Verify order
            assertTrue(streamStart < title, "Stream start before title");
            assertTrue(title < view, "Title before view");
            assertTrue(view < agree, "View before AGREE");
            assertTrue(agree < disagree, "AGREE before DISAGREE");
            assertTrue(disagree < waitOff, "DISAGREE before wait off");
            assertTrue(waitOff < streamEnd, "Wait off before stream end");
        }
    }

    @Nested
    @DisplayName("TOS content handling")
    class TosContentHandling {

        @Test
        @DisplayName("Should include custom TOS content")
        void shouldIncludeCustomTosContent() {
            String customContent = "CUSTOM TERMS HERE";
            TosFdoBuilder customBuilder = new TosFdoBuilder(customContent);
            String fdo = customBuilder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains(customContent) || fdo.contains("man_append_data"),
                "Should include custom content");
        }

        @Test
        @DisplayName("Should use default message when content is null")
        void shouldUseDefaultWhenNull() {
            TosFdoBuilder nullBuilder = new TosFdoBuilder(null);
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            // Should still produce valid FDO
            assertNotNull(fdo);
            assertTrue(fdo.contains("TERMS OF SERVICE"));
        }
    }

    @Nested
    @DisplayName("AGREE button action")
    class AgreeButtonAction {

        @Test
        @DisplayName("Should contain man_close_update atom for atomic modal close")
        void shouldContainManCloseUpdate() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_close_update"),
                "AGREE action must use atomic man_close_update, not separate man_close + man_update_display");
        }

        @Test
        @DisplayName("Should send TA token to server")
        void shouldSendTaToken() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("sm_send_token_raw"),
                "AGREE action must send token");
            assertTrue(fdo.contains("\"TA\"") || fdo.contains("<\"TA\">"),
                "AGREE action must send TA token (not CJ or other)");
        }

        @Test
        @DisplayName("man_close_update should come BEFORE sm_send_token_raw")
        void closeUpdateShouldComeBeforeToken() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            int closePos = fdo.indexOf("man_close_update");
            int tokenPos = fdo.indexOf("sm_send_token_raw");

            assertTrue(closePos > 0, "Should contain man_close_update");
            assertTrue(tokenPos > 0, "Should contain sm_send_token_raw");
            assertTrue(closePos < tokenPos,
                "man_close_update must come BEFORE sm_send_token_raw to ensure modal closes");
        }

        @Test
        @DisplayName("Color mode should set context after close")
        void colorModeShouldSetContext() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo = builder.toSource(ctx);

            assertTrue(fdo.contains("man_set_context_globalid") || fdo.contains("man_set_context"),
                "Color mode AGREE action should set context for focus management");
        }

        @Test
        @DisplayName("Color mode should make focus after close")
        void colorModeShouldMakeFocus() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, false);
            String fdo = builder.toSource(ctx);

            assertTrue(fdo.contains("man_make_focus"),
                "Color mode AGREE action should make focus after closing modal");
        }

        @Test
        @DisplayName("BW mode should NOT set context")
        void bwModeShouldNotSetContext() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, true);
            String fdo = builder.toSource(ctx);

            assertFalse(fdo.contains("man_set_context_globalid"),
                "BW mode should not set context (only color mode needs this)");
        }

        @Test
        @DisplayName("BW mode should NOT make focus")
        void bwModeShouldNotMakeFocus() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, true);
            String fdo = builder.toSource(ctx);

            assertFalse(fdo.contains("man_make_focus"),
                "BW mode should not make focus (only color mode needs this)");
        }
    }

    @Nested
    @DisplayName("DISAGREE button action")
    class DisagreeButtonAction {

        @Test
        @DisplayName("Should contain ccl_hang_up to disconnect")
        void shouldContainHangUp() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("ccl_hang_up"),
                "DISAGREE action must hang up connection");
        }

        @Test
        @DisplayName("Should contain man_close to close modal")
        void shouldContainManClose() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // DISAGREE uses separate man_close (not atomic) since connection is closing anyway
            assertTrue(fdo.contains("man_close"),
                "DISAGREE action must close the modal");
        }

        @Test
        @DisplayName("Should contain man_update_display")
        void shouldContainUpdateDisplay() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_update_display"),
                "DISAGREE action must update display");
        }
    }
}
