/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ConfigureActiveUsernameFdoBuilder;
import com.dialtone.fdo.dsl.builders.LogoutFdoBuilder;
import com.dialtone.fdo.dsl.builders.NewsStoryFdoBuilder;
import com.dialtone.fdo.dsl.builders.ResetWelcomeWindowArtFdoBuilder;
import com.dialtone.fdo.dsl.builders.ServerLogsFdoBuilder;
import com.dialtone.fdo.dsl.builders.UsernameConfigFdoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 4 FDO templates to DSL builders.
 *
 * <p>Batch 4 contains medium complexity UI and post-login templates:</p>
 * <ul>
 *   <li>reset_welcome_window_art.fdo.txt - Clears cached art objects</li>
 *   <li>configure_active_username.fdo.txt - Sets username and online status</li>
 *   <li>logout.fdo.txt - Logout confirmation screen</li>
 * </ul>
 */
@DisplayName("Batch 4: Medium Complexity UI Migrations")
class Batch4MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("ResetWelcomeWindowArtFdoBuilder")
    class ResetWelcomeWindowArtMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = ResetWelcomeWindowArtFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "idb_delete_obj",
                    "idb_delete_obj",
                    "idb_delete_obj",
                    "idb_delete_obj",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = ResetWelcomeWindowArtFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("idb_delete_obj"), "Source should contain idb_delete_obj atoms");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains correct GIDs for art objects")
        void containsCorrectGids() {
            String source = ResetWelcomeWindowArtFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("32-117") || source.contains("32, 117"),
                    "Should contain GID 32-117");
            assertTrue(source.contains("32-5447") || source.contains("32, 5447"),
                    "Should contain GID 32-5447");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("post_login/reset_welcome_window_art", ResetWelcomeWindowArtFdoBuilder.INSTANCE.getGid());
        }
    }

    @Nested
    @DisplayName("ConfigureActiveUsernameFdoBuilder")
    class ConfigureActiveUsernameMigrationTest {

        private static final String TEST_USERNAME = "TestUser";

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            ConfigureActiveUsernameFdoBuilder builder = new ConfigureActiveUsernameFdoBuilder(TEST_USERNAME);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "async_set_screen_name",
                    "async_online",
                    "sm_set_plus_group",
                    "async_auto_launch",
                    "man_update_woff_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            ConfigureActiveUsernameFdoBuilder builder = new ConfigureActiveUsernameFdoBuilder(TEST_USERNAME);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("async_set_screen_name"), "Source should contain async_set_screen_name");
            assertTrue(source.contains(TEST_USERNAME), "Source should contain username");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Config validates null username")
        void configValidatesNullUsername() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigureActiveUsernameFdoBuilder((String) null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            ConfigureActiveUsernameFdoBuilder builder = new ConfigureActiveUsernameFdoBuilder("test");
            assertEquals("post_login/configure_active_username", builder.getGid());
        }
    }

    @Nested
    @DisplayName("LogoutFdoBuilder")
    class LogoutMigrationTest {

        private static final String TEST_MESSAGE = "Thanks for visiting!";

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            LogoutFdoBuilder builder = new LogoutFdoBuilder(TEST_MESSAGE);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_start_stream",
                    "uni_invoke_local",
                    "uni_invoke_local",
                    "man_set_context_global",
                    "sm_set_plus_group",
                    "man_set_context_relative",
                    "man_append_data",
                    "man_end_data",
                    "man_end_context",
                    "man_set_context_relative",
                    "man_append_data",
                    "man_end_data",
                    "man_end_context",
                    "ccl_hang_up",
                    "man_update_woff_end_stream",
                    "uni_start_stream_wait_on",
                    "mat_bool_disabled",
                    "man_update_display",
                    "uni_wait_off_end_stream",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            LogoutFdoBuilder builder = new LogoutFdoBuilder(TEST_MESSAGE);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("ccl_hang_up"), "Source should contain ccl_hang_up");
            assertTrue(source.contains("Goodbye"), "Source should contain goodbye message");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains custom logout message")
        void containsLogoutMessage() {
            LogoutFdoBuilder builder = new LogoutFdoBuilder(TEST_MESSAGE);
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains(TEST_MESSAGE), "Should contain custom logout message");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            LogoutFdoBuilder builder = new LogoutFdoBuilder();
            assertEquals("logout", builder.getGid());
        }
    }

    @Nested
    @DisplayName("UsernameConfigFdoBuilder")
    class UsernameConfigMigrationTest {

        private static final String TEST_USERNAME = "TestUser";

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            UsernameConfigFdoBuilder builder = new UsernameConfigFdoBuilder(TEST_USERNAME);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "idb_set_context",
                    "idb_atr_offset",
                    "idb_append_data",
                    "idb_end_context",
                    "uni_use_last_atom_value",
                    "buf_start_buffer",
                    "buf_set_token",
                    "uni_get_result",
                    "buf_close_buffer",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            UsernameConfigFdoBuilder builder = new UsernameConfigFdoBuilder(TEST_USERNAME);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("idb_set_context"), "Source should contain idb_set_context");
            assertTrue(source.contains("buf_start_buffer"), "Source should contain buf_start_buffer");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Config validates null username")
        void configValidatesNullUsername() {
            assertThrows(IllegalArgumentException.class,
                    () -> new UsernameConfigFdoBuilder((String) null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            UsernameConfigFdoBuilder builder = new UsernameConfigFdoBuilder("test");
            assertEquals("post_login/username_config", builder.getGid());
        }
    }

    @Nested
    @DisplayName("NewsStoryFdoBuilder")
    class NewsStoryMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            NewsStoryFdoBuilder builder = new NewsStoryFdoBuilder("Tech News", "Dec 27", "Content here");
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_start_object",
                    "mat_orientation",
                    "mat_position",
                    "mat_art_id",
                    "man_start_object",
                    "man_append_data",
                    "man_end_object",
                    "man_start_object",
                    "mat_capacity",
                    "man_append_data",
                    "man_end_object",
                    "man_update_display",
                    "man_end_object",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            NewsStoryFdoBuilder builder = new NewsStoryFdoBuilder("News", "Today", "<html>Test</html>");
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("ind_group") || source.contains("IND_GROUP"),
                    "Source should contain ind_group object");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains window title and date")
        void containsTitleAndDate() {
            NewsStoryFdoBuilder builder = new NewsStoryFdoBuilder("Tech News", "Dec 27, 2025", "Story content");
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("Tech News"), "Should contain window title");
            assertTrue(source.contains("Dec 27"), "Should contain date");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            NewsStoryFdoBuilder builder = new NewsStoryFdoBuilder("News", "", "");
            assertEquals("news_story", builder.getGid());
        }
    }

    @Nested
    @DisplayName("ServerLogsFdoBuilder")
    class ServerLogsMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            ServerLogsFdoBuilder builder = new ServerLogsFdoBuilder("Server Logs", "2025-12-27", 100, "Log content");
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_start_object",
                    "mat_bool_modal",
                    "man_start_object",
                    "man_append_data",
                    "man_end_object",
                    "man_start_object",
                    "man_end_object",
                    "man_start_object",
                    "mat_bool_force_scroll",
                    "man_append_data",
                    "man_end_object",
                    "man_start_object",
                    "mat_bool_default",
                    "man_close_update",
                    "man_end_object",
                    "man_end_object",
                    "man_update_display",
                    "uni_wait_off_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            ServerLogsFdoBuilder builder = new ServerLogsFdoBuilder("Logs", "Now", 50, "Test log");
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("mat_bool_modal") || source.contains("BOOL_MODAL"),
                    "Source should be modal");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains log count in subtitle")
        void containsLogCount() {
            ServerLogsFdoBuilder builder = new ServerLogsFdoBuilder("Logs", "Now", 42, "Content");
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("42"), "Should contain log count");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            ServerLogsFdoBuilder builder = new ServerLogsFdoBuilder("Logs", "", 0, "");
            assertEquals("server_logs", builder.getGid());
        }
    }
}
