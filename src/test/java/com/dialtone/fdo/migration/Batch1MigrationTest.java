/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.AckIsFdoBuilder;
import com.dialtone.fdo.dsl.builders.InvokeResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration tests for Batch 1 FDO templates to DSL builders.
 *
 * <p>Batch 1 contains the simplest templates to validate the migration harness:</p>
 * <ul>
 *   <li>noop.fdo.txt - No-operation (no variables)</li>
 *   <li>ack_iS.fdo.txt - IM acknowledgment (1 variable: RESPONSE_ID)</li>
 *   <li>invoke_response.fdo.txt - Invoke local art (1 variable: ART_ID)</li>
 * </ul>
 */
@DisplayName("Batch 1: Low-Risk Static FDO Migrations")
class Batch1MigrationTest {

    private FdoMigrationTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FdoMigrationTestHarness();
    }

    @Nested
    @DisplayName("NoopFdoBuilder")
    class NoopMigrationTest {

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            NoopFdoBuilder builder = NoopFdoBuilder.INSTANCE;
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Verify structural elements in correct order
            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles without error")
        void dslCompiles() {
            NoopFdoBuilder builder = NoopFdoBuilder.INSTANCE;
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Verify DSL compiles successfully
            byte[] binary = harness.compileDsl(builder, RenderingContext.DEFAULT);
            assertNotNull(binary, "DSL should compile successfully");
            assertTrue(binary.length > 0, "DSL should produce output");
        }

        @Test
        @DisplayName("Semantic comparison passes")
        void semanticMatch() {
            NoopFdoBuilder builder = NoopFdoBuilder.INSTANCE;
            String dslSource = builder.toSource(RenderingContext.DEFAULT);
            String expectedSource = """
                    uni_start_stream
                    uni_wait_off
                    uni_end_stream
                    """;

            SemanticResult result = harness.compareSemantics(expectedSource, dslSource);
            assertTrue(result.isEquivalent(),
                    "DSL should be semantically equivalent: " + result.generateDiffReport());
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            assertEquals("noop", NoopFdoBuilder.INSTANCE.getGid());
        }
    }

    @Nested
    @DisplayName("AckIsFdoBuilder")
    class AckIsMigrationTest {

        private static final int TEST_RESPONSE_ID = 42;

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            AckIsFdoBuilder builder = new AckIsFdoBuilder(TEST_RESPONSE_ID);
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Verify structural elements in correct order
            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "man_set_response_id",
                    "man_response_pop",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles and produces valid output")
        void dslCompiles() {
            AckIsFdoBuilder builder = new AckIsFdoBuilder(TEST_RESPONSE_ID);

            // Verify DSL compiles successfully
            byte[] binary = harness.compileDsl(builder, RenderingContext.DEFAULT);
            assertNotNull(binary, "DSL should compile successfully");
            assertTrue(binary.length > 0, "DSL should produce output");
        }

        @Test
        @DisplayName("Different response IDs produce different output")
        void differentResponseIdsDifferentOutput() {
            AckIsFdoBuilder builder1 = new AckIsFdoBuilder(1);
            AckIsFdoBuilder builder2 = new AckIsFdoBuilder(999);

            String source1 = builder1.toSource(RenderingContext.DEFAULT);
            String source2 = builder2.toSource(RenderingContext.DEFAULT);

            assertNotEquals(source1, source2,
                    "Different response IDs should produce different FDO source");
            assertTrue(source1.contains("<1>"));
            assertTrue(source2.contains("<999>"));
        }

        @Test
        @DisplayName("Response ID is accessible")
        void responseIdAccessible() {
            AckIsFdoBuilder builder = new AckIsFdoBuilder(123);
            assertEquals(123, builder.getResponseId());
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            AckIsFdoBuilder builder = new AckIsFdoBuilder(1);
            assertEquals("ack_is", builder.getGid());
        }
    }

    @Nested
    @DisplayName("InvokeResponseFdoBuilder")
    class InvokeResponseMigrationTest {

        private static final FdoGid TEST_ART_ID = FdoGid.of(69, 420);

        @Test
        @DisplayName("DSL output matches template structure")
        void dslMatchesTemplateStructure() {
            InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(TEST_ART_ID);
            String source = builder.toSource(RenderingContext.DEFAULT);

            // Verify structural elements in correct order
            assertTrue(harness.verifyStructure(source,
                    "uni_start_stream",
                    "uni_invoke_local",
                    "uni_wait_off",
                    "uni_end_stream"),
                    "DSL should contain all required atoms in order");
        }

        @Test
        @DisplayName("DSL compiles and produces valid output")
        void dslCompiles() {
            InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(TEST_ART_ID);

            // Verify DSL compiles successfully
            byte[] binary = harness.compileDsl(builder, RenderingContext.DEFAULT);
            assertNotNull(binary, "DSL should compile successfully");
            assertTrue(binary.length > 0, "DSL should produce output");
        }

        @Test
        @DisplayName("Can construct with separate major/minor components")
        void constructWithComponents() {
            InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(69, 420);
            assertEquals(FdoGid.of(69, 420), builder.getArtId());
        }

        @Test
        @DisplayName("Different art IDs produce different output")
        void differentArtIdsDifferentOutput() {
            InvokeResponseFdoBuilder builder1 = new InvokeResponseFdoBuilder(69, 420);
            InvokeResponseFdoBuilder builder2 = new InvokeResponseFdoBuilder(32, 117);

            String source1 = builder1.toSource(RenderingContext.DEFAULT);
            String source2 = builder2.toSource(RenderingContext.DEFAULT);

            assertNotEquals(source1, source2,
                    "Different art IDs should produce different FDO source");
        }

        @Test
        @DisplayName("Null art ID throws exception")
        void nullArtIdThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvokeResponseFdoBuilder(null));
        }

        @Test
        @DisplayName("GID is correct")
        void gidIsCorrect() {
            InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(1, 1);
            assertEquals("invoke_response", builder.getGid());
        }
    }

    @Nested
    @DisplayName("Cross-Platform Compatibility")
    class CrossPlatformTest {

        @Test
        @DisplayName("Noop is platform-independent")
        void noopPlatformIndependent() {
            String windowsSource = NoopFdoBuilder.INSTANCE.toSource(
                    new RenderingContext(ClientPlatform.WINDOWS, false));
            String macSource = NoopFdoBuilder.INSTANCE.toSource(
                    new RenderingContext(ClientPlatform.MAC, false));

            assertEquals(windowsSource, macSource,
                    "Noop should produce identical output across platforms");
        }

        @Test
        @DisplayName("AckIs is platform-independent")
        void ackIsPlatformIndependent() {
            AckIsFdoBuilder builder = new AckIsFdoBuilder(42);

            String windowsSource = builder.toSource(
                    new RenderingContext(ClientPlatform.WINDOWS, false));
            String macSource = builder.toSource(
                    new RenderingContext(ClientPlatform.MAC, false));

            assertEquals(windowsSource, macSource,
                    "AckIs should produce identical output across platforms");
        }

        @Test
        @DisplayName("InvokeResponse is platform-independent")
        void invokeResponsePlatformIndependent() {
            InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(69, 420);

            String windowsSource = builder.toSource(
                    new RenderingContext(ClientPlatform.WINDOWS, false));
            String macSource = builder.toSource(
                    new RenderingContext(ClientPlatform.MAC, false));

            assertEquals(windowsSource, macSource,
                    "InvokeResponse should produce identical output across platforms");
        }
    }
}
