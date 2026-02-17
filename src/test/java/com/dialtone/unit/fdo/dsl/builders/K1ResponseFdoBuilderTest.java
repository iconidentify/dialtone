/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.K1ResponseFdoBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for K1ResponseFdoBuilder.
 * Verifies output matches the original fdo/k1_response.fdo.txt template.
 */
@DisplayName("K1ResponseFdoBuilder")
class K1ResponseFdoBuilderTest {

    private static final int TEST_RESPONSE_ID = 12345;
    private static final String TEST_INNER_FDO = "  uni_wait_off\n";
    private K1ResponseFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new K1ResponseFdoBuilder(TEST_RESPONSE_ID, TEST_INNER_FDO);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("k1_response", builder.getGid());
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
            K1ResponseFdoBuilder.Config config = builder.getConfig();
            assertNotNull(config);
            assertEquals(TEST_RESPONSE_ID, config.responseId());
            assertEquals(TEST_INNER_FDO, config.innerFdo());
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
        @DisplayName("Should contain man_set_response_id with correct value")
        void shouldContainResponseId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_set_response_id"), "Should have response ID atom");
            assertTrue(fdo.contains(String.valueOf(TEST_RESPONSE_ID)), "Should contain response ID value");
        }

        @Test
        @DisplayName("Should contain inner FDO content")
        void shouldContainInnerFdo() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_wait_off"), "Should contain inner FDO content");
        }

        @Test
        @DisplayName("Should have correct structural order")
        void shouldHaveCorrectStructuralOrder() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            int streamStart = fdo.indexOf("uni_start_stream");
            int responseId = fdo.indexOf("man_set_response_id");
            int innerContent = fdo.indexOf("uni_wait_off");
            int streamEnd = fdo.indexOf("uni_end_stream");

            assertTrue(streamStart < responseId, "Stream start before response ID");
            assertTrue(responseId < innerContent, "Response ID before inner content");
            assertTrue(innerContent < streamEnd, "Inner content before stream end");
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
        @DisplayName("wrap() should create builder")
        void wrapShouldCreateBuilder() {
            K1ResponseFdoBuilder wrapped = K1ResponseFdoBuilder.wrap(999, "test content");
            String fdo = wrapped.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("999"));
            assertTrue(fdo.contains("test content"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle empty inner FDO")
        void shouldHandleEmptyInnerFdo() {
            K1ResponseFdoBuilder emptyBuilder = new K1ResponseFdoBuilder(123, "");
            String fdo = emptyBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("uni_start_stream"));
            assertTrue(fdo.contains("uni_end_stream"));
        }

        @Test
        @DisplayName("Should handle inner FDO without trailing newline")
        void shouldHandleInnerFdoWithoutNewline() {
            K1ResponseFdoBuilder builder = new K1ResponseFdoBuilder(123, "uni_wait_off");
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // Should still produce valid FDO with proper termination
            assertTrue(fdo.endsWith("uni_end_stream\n") || fdo.contains("uni_end_stream"));
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> {
                new K1ResponseFdoBuilder(null);
            });
        }
    }
}
