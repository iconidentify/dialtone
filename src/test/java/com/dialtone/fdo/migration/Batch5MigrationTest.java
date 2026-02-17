/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.GuestLoginFdoBuilder;
import com.dialtone.fdo.dsl.builders.ReceiveImFdoBuilder;
import com.dialtone.fdo.dsl.builders.WelcomeScreenFdoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 5 FDO templates to DSL builders.
 *
 * <p>Batch 5 contains high priority core UI templates:</p>
 * <ul>
 *   <li>receive_im.fdo.txt - IM display window (has BW variant)</li>
 *   <li>guest_login.fdo.txt - Guest authentication modal</li>
 *   <li>welcome_screen.fdo.txt - Main welcome screen with news</li>
 * </ul>
 */
@DisplayName("Batch 5: High Priority Core UI Migrations")
class Batch5MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("WelcomeScreenFdoBuilder")
    class WelcomeScreenMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(
                    "TestUser",
                    "Tech headline",
                    "News headline",
                    "Financial headline",
                    "Sports headline",
                    "Entertainment headline"
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_wait_on",
                    "uni_invoke_local",
                    "mat_title",
                    "man_set_context_global",
                    "man_make_focus",
                    "man_set_context_relative",
                    "man_replace_data",
                    "man_end_context",
                    "mat_art_id",
                    "man_force_update",
                    "man_update_display",
                    "sm_send_token_raw",
                    "uni_wait_off_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(
                    "TestUser", "Tech", "News", "Finance", "Sports", "Entertainment"
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("uni_invoke_local"), "Source should contain uni_invoke_local");
            assertTrue(source.contains("Welcome, TestUser"), "Source should contain welcome message");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains all news category contexts")
        void containsNewsCategoryContexts() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(
                    "User", "TechNews", "GeneralNews", "FinanceNews", "SportsNews", "CultureNews"
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Check relative contexts for each category
            assertTrue(source.contains("man_set_context_relative <3>"), "Should have tech context");
            assertTrue(source.contains("man_set_context_relative <10>"), "Should have news context");
            assertTrue(source.contains("man_set_context_relative <12>"), "Should have financial context");
            assertTrue(source.contains("man_set_context_relative <14>"), "Should have sports context");
            assertTrue(source.contains("man_set_context_relative <16>"), "Should have culture context");
        }

        @Test
        @DisplayName("Contains all category icons")
        void containsCategoryIcons() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(
                    "User", "", "", "", "", ""
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Check icon contexts
            assertTrue(source.contains("man_set_context_relative <17>"), "Should have news icon context");
            assertTrue(source.contains("man_set_context_relative <18>"), "Should have financial icon context");
            assertTrue(source.contains("man_set_context_relative <19>"), "Should have sports icon context");
            assertTrue(source.contains("man_set_context_relative <20>"), "Should have culture icon context");
            assertTrue(source.contains("man_set_context_relative <21>"), "Should have tech icon context");
        }

        @Test
        @DisplayName("Contains icon click actions with tokens")
        void containsIconClickActions() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(
                    "User", "", "", "", "", ""
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("NX1"), "Should have news token");
            assertTrue(source.contains("NX2"), "Should have sports token");
            assertTrue(source.contains("NX3"), "Should have financial token");
            assertTrue(source.contains("NX4"), "Should have culture token");
            assertTrue(source.contains("NX5"), "Should have tech token");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder("User", "", "", "", "", "");
            assertEquals("welcome_screen", builder.getGid());
        }

        @Test
        @DisplayName("Default config handles null screenname")
        void defaultConfigHandlesNullScreenname() {
            WelcomeScreenFdoBuilder builder = new WelcomeScreenFdoBuilder(null, null, null, null, null, null);
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("Welcome, Guest"), "Should default to Guest screenname");
        }
    }

    @Nested
    @DisplayName("ReceiveImFdoBuilder")
    class ReceiveImMigrationTest {

        private static final ButtonTheme TEST_THEME = new ButtonTheme(
                new int[]{128, 128, 128},
                new int[]{0, 0, 0},
                new int[]{192, 192, 192},
                new int[]{64, 64, 64}
        );

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    42, "FromUser", "Hello!", 123, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_preset_gid",
                    "if_last_return_true_then",
                    "uni_sync_skip",
                    "man_start_object",
                    "mat_orientation",
                    "mat_object_id",
                    "mat_title",
                    "man_set_response_id",
                    "man_start_object",
                    "mat_bool_invisible",
                    "man_replace_data",
                    "man_end_object",
                    "man_start_object",
                    "mat_relative_tag",
                    "man_end_object",
                    "man_start_object",
                    "mat_capacity",
                    "man_end_object",
                    "man_start_object",
                    "mat_title",
                    "man_end_object",
                    "man_end_object",
                    "uni_sync_skip",
                    "man_set_context_relative",
                    "man_append_data",
                    "man_end_context",
                    "man_make_focus",
                    "man_update_display",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    42, "TestSender", "Hello world", 100, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("Instant Message:"), "Source should contain IM title prefix");
            assertTrue(source.contains("TestSender"), "Source should contain sender name");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Color mode includes button colors and trigger style")
        void colorModeIncludesButtonColors() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "User", "Msg", 1, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("mat_color_face"), "Color mode should include face color");
            assertTrue(source.contains("mat_color_text"), "Color mode should include text color");
            assertTrue(source.contains("mat_trigger_style"), "Color mode should include trigger style");
        }

        @Test
        @DisplayName("BW mode excludes button colors and trigger style")
        void bwModeExcludesButtonColors() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "User", "Msg", 1, TEST_THEME
            );
            RenderingContext bwContext = new RenderingContext(
                    RenderingContext.DEFAULT.getPlatform(),
                    true  // lowColorMode = true
            );
            String source = builder.toSource(bwContext);

            assertFalse(source.contains("mat_color_face"), "BW mode should not include face color");
            assertFalse(source.contains("mat_trigger_style"), "BW mode should not include trigger style");
        }

        @Test
        @DisplayName("Null buttonTheme treated as BW mode")
        void nullButtonThemeTreatedAsBwMode() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "User", "Msg", 1, null
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertFalse(source.contains("mat_color_face"), "Null theme should not include colors");
        }

        @Test
        @DisplayName("Contains Send button with extraction action")
        void containsSendButtonAction() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "User", "Msg", 1, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("de_start_extraction"), "Should contain extraction start");
            assertTrue(source.contains("buf_set_token"), "Should contain token setting");
            assertTrue(source.contains("de_get_data"), "Should contain data extraction");
            assertTrue(source.contains("de_end_extraction"), "Should contain extraction end");
        }

        @Test
        @DisplayName("Contains Cancel button with close action")
        void containsCancelButtonAction() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "User", "Msg", 1, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("man_close"), "Should contain close action");
        }

        @Test
        @DisplayName("Message contains sender prefix and line break")
        void messageContainsSenderAndLineBreak() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, "Sender", "TestMessage", 1, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("Sender: TestMessage"), "Should contain formatted message");
        }

        @Test
        @DisplayName("Config validates null values")
        void configValidatesNullValues() {
            // Config should handle nulls gracefully by defaulting to empty strings
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(
                    1, null, null, 1, TEST_THEME
            );
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "Should handle null fromUser and message");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            ReceiveImFdoBuilder builder = new ReceiveImFdoBuilder(1, "", "", 1, null);
            assertEquals("receive_im", builder.getGid());
        }
    }

    @Nested
    @DisplayName("GuestLoginFdoBuilder")
    class GuestLoginMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_preset_gid",
                    "if_last_return_false_then",
                    "uni_sync_skip",
                    "uni_start_stream",
                    "man_start_object",
                    "mat_object_id",
                    "mat_orientation",
                    "mat_precise_width",
                    "mat_precise_height",
                    "mat_bool_modal",
                    "man_start_object",
                    "mat_art_id",
                    "man_start_sibling",
                    "man_append_data",
                    "man_start_sibling",
                    "mat_relative_tag",
                    "man_start_sibling",
                    "mat_bool_protected_input",
                    "man_start_sibling",
                    "man_close",
                    "man_start_sibling",
                    "mat_bool_default",
                    "de_ez_send_form",
                    "man_update_display",
                    "uni_end_stream",
                    "man_update_woff_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("mat_bool_modal"), "Source should be modal");
            assertTrue(source.contains("Guest Sign-On"), "Source should contain title");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains correct object ID")
        void containsCorrectObjectId() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("32-494") || source.contains("32, 494"),
                    "Should contain object ID 32-494");
        }

        @Test
        @DisplayName("Contains preset GID check")
        void containsPresetGidCheck() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("man_preset_gid <0-52>") || source.contains("man_preset_gid <0, 52>"),
                    "Should contain preset GID check");
            assertTrue(source.contains("if_last_return_false_then"), "Should contain conditional");
        }

        @Test
        @DisplayName("Contains username field with relative tag 1")
        void containsUsernameField() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("Please enter your screen name"), "Should contain username prompt");
            assertTrue(source.contains("mat_relative_tag <1>"), "Should have username tag 1");
        }

        @Test
        @DisplayName("Contains password field with relative tag 2 and protection")
        void containsPasswordField() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("Please enter your password"), "Should contain password prompt");
            assertTrue(source.contains("mat_relative_tag <2>"), "Should have password tag 2");
            assertTrue(source.contains("mat_bool_protected_input"), "Password should be protected");
        }

        @Test
        @DisplayName("Contains Cancel button with close action")
        void containsCancelButton() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("Cancel"), "Should contain Cancel button");
            assertTrue(source.contains("man_close"), "Cancel should close window");
            assertTrue(source.contains("uni_invoke_local <20-0-0>") ||
                       source.contains("uni_invoke_local <20, 0, 0>"),
                    "Cancel should invoke logout");
        }

        @Test
        @DisplayName("Contains OK button with form submission")
        void containsOkButton() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("mat_bool_default"), "OK should be default button");
            assertTrue(source.contains("de_ez_send_form"), "OK should send form");
        }

        @Test
        @DisplayName("Window dimensions are correct")
        void windowDimensionsCorrect() {
            String source = GuestLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("mat_precise_width <325>"), "Width should be 325");
            assertTrue(source.contains("mat_precise_height <230>"), "Height should be 230");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("guest_login", GuestLoginFdoBuilder.INSTANCE.getGid());
        }
    }
}
