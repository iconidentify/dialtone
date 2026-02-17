/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DodNotAvailableFdoBuilder;
import com.dialtone.fdo.dsl.builders.DodResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F1AtomStreamResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F1DodFailedFdoBuilder;
import com.dialtone.fdo.dsl.builders.F2IdbResponseFdoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 2 FDO templates to DSL builders.
 *
 * <p>Batch 2 contains the DOD response pipeline templates:</p>
 * <ul>
 *   <li>dod_response.fdo.txt - Main DOD art response</li>
 *   <li>f2_idb_response.fdo.txt - f2 IDB art response</li>
 *   <li>f1_atom_stream_response.fdo.txt - f1 atom stream response</li>
 * </ul>
 */
@DisplayName("Batch 2: DOD Response Pipeline Migrations")
class Batch2MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("DodResponseFdoBuilder")
    class DodResponseMigrationTest {

        private static final int TEST_TRANSACTION_ID = 42;
        private static final String TEST_FORM_ID = "32-1234";
        private static final FdoGid TEST_GID = FdoGid.of(1, 0, 5678);
        private static final byte[] TEST_DATA = "HELLO".getBytes(); // Binary data

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            DodResponseFdoBuilder builder = new DodResponseFdoBuilder(
                    TEST_TRANSACTION_ID, TEST_FORM_ID, TEST_GID, TEST_DATA);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "dod_start",
                    "uni_transaction_id",
                    "dod_no_hints",
                    "dod_form_id",
                    "dod_gid",
                    "dod_type",
                    "dod_data",
                    "dod_end_data",
                    "dod_close_form",
                    "dod_end",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            DodResponseFdoBuilder builder = new DodResponseFdoBuilder(
                    TEST_TRANSACTION_ID, TEST_FORM_ID, TEST_GID, TEST_DATA);

            // Generate source and verify it can be compiled
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("dod_data"), "Source should contain dod_data atom");

            // Verify the source compiles (the harness compiles it internally)
            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Config record validates null parameters")
        void configValidatesNulls() {
            assertThrows(IllegalArgumentException.class,
                    () -> new DodResponseFdoBuilder.Config(1, null, TEST_GID, TEST_DATA));
            assertThrows(IllegalArgumentException.class,
                    () -> new DodResponseFdoBuilder.Config(1, TEST_FORM_ID, null, TEST_DATA));
            assertThrows(IllegalArgumentException.class,
                    () -> new DodResponseFdoBuilder.Config(1, TEST_FORM_ID, TEST_GID, null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            DodResponseFdoBuilder builder = new DodResponseFdoBuilder(1, "form", FdoGid.of(1, 1), new byte[]{0});
            assertEquals("dod_response", builder.getGid());
        }
    }

    @Nested
    @DisplayName("F2IdbResponseFdoBuilder")
    class F2IdbResponseMigrationTest {

        private static final String TEST_OBJ_TYPE = "p";
        private static final FdoGid TEST_GID = FdoGid.of(69, 420);
        private static final int TEST_DATA_LENGTH = 100;
        private static final byte[] TEST_DATA = new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            F2IdbResponseFdoBuilder builder = new F2IdbResponseFdoBuilder(
                    TEST_OBJ_TYPE, TEST_GID, TEST_DATA_LENGTH, TEST_DATA);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_start_stream",
                    "idb_start_obj",
                    "idb_dod_progress_gauge",
                    "idb_atr_globalid",
                    "idb_atr_length",
                    "idb_atr_dod",
                    "idb_append_data",
                    "idb_end_obj",
                    "uni_end_stream",
                    "man_update_woff_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            F2IdbResponseFdoBuilder builder = new F2IdbResponseFdoBuilder(
                    TEST_OBJ_TYPE, TEST_GID, TEST_DATA_LENGTH, TEST_DATA);

            // Generate source and verify it can be compiled
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("idb_append_data"), "Source should contain idb_append_data atom");

            // Verify the source compiles
            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Config record validates null parameters")
        void configValidatesNulls() {
            assertThrows(IllegalArgumentException.class,
                    () -> new F2IdbResponseFdoBuilder.Config(null, TEST_GID, 100, TEST_DATA));
            assertThrows(IllegalArgumentException.class,
                    () -> new F2IdbResponseFdoBuilder.Config(TEST_OBJ_TYPE, null, 100, TEST_DATA));
            assertThrows(IllegalArgumentException.class,
                    () -> new F2IdbResponseFdoBuilder.Config(TEST_OBJ_TYPE, TEST_GID, 100, null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            F2IdbResponseFdoBuilder builder = new F2IdbResponseFdoBuilder("p", FdoGid.of(1, 1), 10, new byte[]{0});
            assertEquals("f2_idb_response", builder.getGid());
        }
    }

    @Nested
    @DisplayName("F1AtomStreamResponseFdoBuilder")
    class F1AtomStreamResponseMigrationTest {

        private static final FdoGid TEST_GID = FdoGid.of(32, 256);
        private static final int TEST_DATA_LENGTH = 50;
        private static final byte[] TEST_DATA = new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF};

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            F1AtomStreamResponseFdoBuilder builder = new F1AtomStreamResponseFdoBuilder(
                    TEST_GID, TEST_DATA_LENGTH, TEST_DATA);
            String source = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_start_stream",
                    "idb_start_obj",
                    "idb_dod_progress_gauge",
                    "idb_atr_globalid",
                    "idb_atr_length",
                    "idb_atr_dod",
                    "idb_append_data",
                    "idb_end_obj",
                    "idb_set_context",
                    "idb_atr_dod",
                    "idb_end_context",
                    "uni_end_stream",
                    "uni_invoke_local",
                    "man_update_woff_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            F1AtomStreamResponseFdoBuilder builder = new F1AtomStreamResponseFdoBuilder(
                    TEST_GID, TEST_DATA_LENGTH, TEST_DATA);

            // Generate source and verify it can be compiled
            String source = builder.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("idb_append_data"), "Source should contain idb_append_data atom");
            assertTrue(source.contains("uni_invoke_local"), "Source should contain uni_invoke_local atom");

            // Verify the source compiles
            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Config record validates null parameters")
        void configValidatesNulls() {
            assertThrows(IllegalArgumentException.class,
                    () -> new F1AtomStreamResponseFdoBuilder.Config(null, 100, TEST_DATA));
            assertThrows(IllegalArgumentException.class,
                    () -> new F1AtomStreamResponseFdoBuilder.Config(TEST_GID, 100, null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            F1AtomStreamResponseFdoBuilder builder = new F1AtomStreamResponseFdoBuilder(FdoGid.of(1, 1), 10, new byte[]{0});
            assertEquals("f1_atom_stream_response", builder.getGid());
        }
    }

    @Nested
    @DisplayName("F1DodFailedFdoBuilder")
    class F1DodFailedMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = F1DodFailedFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = F1DodFailedFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("uni_wait_off"), "Source should contain uni_wait_off atom");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("f1_dod_failed", F1DodFailedFdoBuilder.INSTANCE.getGid());
        }
    }

    @Nested
    @DisplayName("DodNotAvailableFdoBuilder")
    class DodNotAvailableMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            String source = DodNotAvailableFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);

            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles successfully")
        void dslCompiles() {
            String source = DodNotAvailableFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
            assertNotNull(source, "DSL should generate source");
            assertTrue(source.contains("uni_wait_off"), "Source should contain uni_wait_off atom");

            try {
                var chunks = harness.getCompiler().compileFdoScriptToP3Chunks(source, "AT", 0);
                assertNotNull(chunks, "DSL should compile to chunks");
                assertTrue(chunks.size() > 0, "DSL should produce chunks");
            } catch (Exception e) {
                fail("DSL source should compile: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("dod_not_available", DodNotAvailableFdoBuilder.INSTANCE.getGid());
        }
    }
}
