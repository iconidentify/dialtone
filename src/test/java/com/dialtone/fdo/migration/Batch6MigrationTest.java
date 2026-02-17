/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.Gid32_294FdoBuilder;
import com.dialtone.fdo.dsl.builders.Gid69_420FdoBuilder;
import com.dialtone.fdo.dsl.builders.Gid69_421FdoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 6 replace_client_fdo templates to DSL builders.
 *
 * <p>Batch 6 contains complex UI forms used as replacements for client FDOs:</p>
 * <ul>
 *   <li>32-294 - Network News window</li>
 *   <li>69-420 - Chat input form (already migrated)</li>
 *   <li>69-421 - Map form (already migrated)</li>
 * </ul>
 *
 * <p>Note: Many replace_client_fdo templates use advanced features like VRM
 * commands and rich text that require additional DSL support.</p>
 */
@DisplayName("Batch 6: Replace Client FDO Migrations")
class Batch6MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("Gid32_294FdoBuilder (Network News)")
    class Gid32_294MigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = Gid32_294FdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_start_object",
                    "mat_object_id",
                    "mat_orientation",
                    "mat_position",
                    "man_start_object",
                    "mat_height",
                    "mat_capacity",
                    "mat_relative_tag",
                    "mat_bool_writeable",
                    "man_end_object",
                    "man_update_display",
                    "man_end_object",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = Gid32_294FdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("Network News"), "Source should contain window title");

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
            String source = Gid32_294FdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("32-294") || source.contains("32, 294"),
                    "Should contain object ID 32-294");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("32-294", Gid32_294FdoBuilder.INSTANCE.getGid());
        }
    }

    @Nested
    @DisplayName("Gid69_420FdoBuilder (Chat Input)")
    class Gid69_420MigrationTest {

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = new Gid69_420FdoBuilder().toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains object ID")
        void containsObjectId() {
            String source = new Gid69_420FdoBuilder().toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("69-420") || source.contains("69, 420"),
                    "Should contain object ID 69-420");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("69-420", new Gid69_420FdoBuilder().getGid());
        }
    }

    @Nested
    @DisplayName("Gid69_421FdoBuilder (Map)")
    class Gid69_421MigrationTest {

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = new Gid69_421FdoBuilder().toSource(RenderingContext.DEFAULT);

            assertNotNull(source, "DSL should generate source");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Contains object ID")
        void containsObjectId() {
            String source = new Gid69_421FdoBuilder().toSource(RenderingContext.DEFAULT);
            assertTrue(source.contains("69-421") || source.contains("69, 421"),
                    "Should contain object ID 69-421");
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("69-421", new Gid69_421FdoBuilder().getGid());
        }
    }
}
