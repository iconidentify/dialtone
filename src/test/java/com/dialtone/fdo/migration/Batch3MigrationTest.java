/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DownloadErrorFdoBuilder;
import com.dialtone.fdo.dsl.builders.IncorrectLoginFdoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 3 FDO templates to DSL builders.
 *
 * <p>Batch 3 contains error/status templates:</p>
 * <ul>
 *   <li>download_error.fdo.txt - Download failure alert</li>
 *   <li>incorrect_login.fdo.txt - Authentication failure response</li>
 * </ul>
 */
@DisplayName("Batch 3: Error/Status Template Migrations")
class Batch3MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("DownloadErrorFdoBuilder")
    class DownloadErrorMigrationTest {

        private static final String TEST_ERROR_MESSAGE = "File transfer failed.";

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder(TEST_ERROR_MESSAGE);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "async_alert",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder(TEST_ERROR_MESSAGE);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("async_alert"), "Source should contain async_alert atom");
            assertTrue(source.contains(TEST_ERROR_MESSAGE), "Source should contain error message");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Default error message is used when null")
        void defaultErrorMessageWhenNull() {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder((String) null);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(source.contains("Download failed"),
                    "Should use default error message when null provided");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            DownloadErrorFdoBuilder builder = new DownloadErrorFdoBuilder();
            assertEquals("download_error", builder.getGid());
        }
    }

    @Nested
    @DisplayName("IncorrectLoginFdoBuilder")
    class IncorrectLoginMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = IncorrectLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_start_stream",
                    "async_alert",
                    "ccl_hang_up",
                    "man_update_woff_end_stream",
                    "uni_start_stream_wait_on",
                    "man_set_context_global",
                    "man_set_context_relative",
                    "mat_bool_disabled",
                    "man_end_context",
                    "man_update_display",
                    "man_end_context",
                    "uni_wait_off_end_stream",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = IncorrectLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("ccl_hang_up"), "Source should contain ccl_hang_up atom");
            assertTrue(source.contains("Incorrect username or password"),
                    "Source should contain error message");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains login form GID")
        void containsLoginFormGid() {
            String source = IncorrectLoginFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("32-6086") || source.contains("32, 6086"),
                    "Source should reference login form GID 32-6086");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("incorrect_login", IncorrectLoginFdoBuilder.INSTANCE.getGid());
        }
    }
}
