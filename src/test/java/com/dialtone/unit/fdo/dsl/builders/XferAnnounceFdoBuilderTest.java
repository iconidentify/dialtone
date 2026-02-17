/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.XferAnnounceFdoBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XferAnnounceFdoBuilder.
 * Verifies output matches the original fdo/xfer_announce.fdo.txt template.
 *
 * Note: The original template has all xfer atoms commented out, so the
 * builder currently outputs an empty stream. This test verifies that behavior.
 */
@DisplayName("XferAnnounceFdoBuilder")
class XferAnnounceFdoBuilderTest {

    private static final String TEST_LIBRARY = "testlib";
    private static final int TEST_REQUEST_ID = 12345;
    private static final String TEST_TITLE = "Test File";
    private static final long TEST_FILE_SIZE = 1024L;
    private static final String TEST_FILE_NAME = "test.txt";
    private static final long TEST_CREATE_DATE = 1704067200L;  // 2024-01-01

    private XferAnnounceFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new XferAnnounceFdoBuilder(
            TEST_LIBRARY, TEST_REQUEST_ID, TEST_TITLE,
            TEST_FILE_SIZE, TEST_FILE_NAME, TEST_CREATE_DATE);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("xfer_announce", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }

        @Test
        @DisplayName("Should return config")
        void shouldReturnConfig() {
            XferAnnounceFdoBuilder.Config config = builder.getConfig();
            assertNotNull(config);
            assertEquals(TEST_LIBRARY, config.library());
            assertEquals(TEST_REQUEST_ID, config.requestId());
            assertEquals(TEST_TITLE, config.title());
            assertEquals(TEST_FILE_SIZE, config.fileSize());
            assertEquals(TEST_FILE_NAME, config.fileName());
            assertEquals(TEST_CREATE_DATE, config.createDate());
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
        @DisplayName("Should produce minimal output matching original template")
        void shouldProduceMinimalOutput() {
            // Original template has all xfer atoms commented out
            // So we expect just uni_start_stream/uni_end_stream
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // Verify it's a minimal empty stream
            assertTrue(fdo.contains("uni_start_stream"));
            assertTrue(fdo.contains("uni_end_stream"));

            // Note: xfer atoms are NOT present because original had them commented
            // When xfer protocol is fully implemented, these tests should be updated
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            String fdo1 = builder.toSource(RenderingContext.DEFAULT);
            String fdo2 = builder.toSource(RenderingContext.DEFAULT);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("announce() should create builder")
        void announceShouldCreateBuilder() {
            XferAnnounceFdoBuilder announced = XferAnnounceFdoBuilder.announce(
                "lib", 999, "File", 2048, "file.bin", 0);
            String fdo = announced.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null library as empty")
        void shouldHandleNullLibrary() {
            XferAnnounceFdoBuilder nullBuilder = new XferAnnounceFdoBuilder(
                null, 1, "title", 100, "file.txt", 0);
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
        }

        @Test
        @DisplayName("Should handle null title as empty")
        void shouldHandleNullTitle() {
            XferAnnounceFdoBuilder nullBuilder = new XferAnnounceFdoBuilder(
                "lib", 1, null, 100, "file.txt", 0);
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
        }

        @Test
        @DisplayName("Should handle null fileName as empty")
        void shouldHandleNullFileName() {
            XferAnnounceFdoBuilder nullBuilder = new XferAnnounceFdoBuilder(
                "lib", 1, "title", 100, null, 0);
            String fdo = nullBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> {
                new XferAnnounceFdoBuilder(null);
            });
        }
    }
}
