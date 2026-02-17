/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.IdbResetAndSendFdoBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdbResetAndSendFdoBuilder.
 * Verifies output matches the original fdo/idb_reset_and_send.fdo.txt template.
 */
@DisplayName("IdbResetAndSendFdoBuilder")
class IdbResetAndSendFdoBuilderTest {

    private static final String TEST_OBJ_TYPE = "a";  // atom stream
    private static final FdoGid TEST_ATOM_GID = FdoGid.of(69, 420);
    private static final byte[] TEST_DATA = new byte[]{0x01, 0x02, 0x03, 0x04};
    private IdbResetAndSendFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new IdbResetAndSendFdoBuilder(TEST_OBJ_TYPE, TEST_ATOM_GID, TEST_DATA.length, TEST_DATA);
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("idb_reset_and_send", builder.getGid());
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
            IdbResetAndSendFdoBuilder.Config config = builder.getConfig();
            assertNotNull(config);
            assertEquals(TEST_OBJ_TYPE, config.objType());
            assertEquals(TEST_ATOM_GID, config.atomGid());
            assertEquals(TEST_DATA.length, config.dataLength());
            assertArrayEquals(TEST_DATA, config.data());
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
        @DisplayName("Should contain uni_start_stream")
        void shouldContainStreamStart() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("uni_start_stream"), "Should have stream start");
        }

        @Test
        @DisplayName("Should contain idb_delete_obj for reset")
        void shouldContainDeleteObj() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_delete_obj"), "Should delete existing object");
        }

        @Test
        @DisplayName("Should contain nested uni_start_stream for IDB object")
        void shouldContainNestedStream() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            // Should have two uni_start_stream: outer wrapper and inner IDB stream
            int firstStream = fdo.indexOf("uni_start_stream");
            int secondStream = fdo.indexOf("uni_start_stream", firstStream + 1);
            assertTrue(secondStream > firstStream, "Should have nested stream for IDB");
        }

        @Test
        @DisplayName("Should contain idb_start_obj with type")
        void shouldContainStartObj() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_start_obj"), "Should start IDB object");
        }

        @Test
        @DisplayName("Should contain idb_dod_progress_gauge")
        void shouldContainProgressGauge() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_dod_progress_gauge"), "Should have progress gauge");
        }

        @Test
        @DisplayName("Should contain idb_atr_globalid")
        void shouldContainGlobalId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_atr_globalid"), "Should have global ID attribute");
        }

        @Test
        @DisplayName("Should contain idb_atr_length")
        void shouldContainLength() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_atr_length"), "Should have length attribute");
        }

        @Test
        @DisplayName("Should contain idb_atr_dod")
        void shouldContainDod() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_atr_dod"), "Should have DOD attribute");
        }

        @Test
        @DisplayName("Should contain idb_atr_offset")
        void shouldContainOffset() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_atr_offset"), "Should have offset attribute");
        }

        @Test
        @DisplayName("Should contain idb_append_data")
        void shouldContainAppendData() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_append_data"), "Should append data");
        }

        @Test
        @DisplayName("Should contain idb_end_obj")
        void shouldContainEndObj() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("idb_end_obj"), "Should end IDB object");
        }

        @Test
        @DisplayName("Should contain man_update_woff_end_stream")
        void shouldContainWoffEndStream() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("man_update_woff_end_stream"),
                "Should have wait-off end stream");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            String fdo1 = builder.toSource(RenderingContext.DEFAULT);
            String fdo2 = builder.toSource(RenderingContext.DEFAULT);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }

        @Test
        @DisplayName("Should have correct structural order")
        void shouldHaveCorrectStructuralOrder() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            int outerStart = fdo.indexOf("uni_start_stream");
            int deleteObj = fdo.indexOf("idb_delete_obj");
            int innerStart = fdo.indexOf("uni_start_stream", outerStart + 1);
            int startObj = fdo.indexOf("idb_start_obj");
            int appendData = fdo.indexOf("idb_append_data");
            int endObj = fdo.indexOf("idb_end_obj");
            int woffEnd = fdo.indexOf("man_update_woff_end_stream");

            assertTrue(outerStart < deleteObj, "Outer stream before delete");
            assertTrue(deleteObj < innerStart, "Delete before inner stream");
            assertTrue(innerStart < startObj, "Inner stream before start obj");
            assertTrue(startObj < appendData, "Start obj before append data");
            assertTrue(appendData < endObj, "Append data before end obj");
            assertTrue(endObj < woffEnd, "End obj before woff end stream");
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("reset() should create builder")
        void resetShouldCreateBuilder() {
            byte[] data = new byte[]{0x10, 0x20};
            IdbResetAndSendFdoBuilder resetBuilder = IdbResetAndSendFdoBuilder.reset(
                "p", FdoGid.of(32, 100), data);
            String fdo = resetBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("idb_delete_obj"));
            assertTrue(fdo.contains("idb_start_obj"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle empty data")
        void shouldHandleEmptyData() {
            IdbResetAndSendFdoBuilder emptyBuilder = new IdbResetAndSendFdoBuilder(
                "a", FdoGid.of(1, 1), 0, new byte[0]);
            String fdo = emptyBuilder.toSource(RenderingContext.DEFAULT);

            assertNotNull(fdo);
            assertTrue(fdo.contains("idb_atr_length"));
        }

        @Test
        @DisplayName("Should reject null objType")
        void shouldRejectNullObjType() {
            assertThrows(IllegalArgumentException.class, () -> {
                new IdbResetAndSendFdoBuilder(null, TEST_ATOM_GID, 4, TEST_DATA);
            });
        }

        @Test
        @DisplayName("Should reject null atomGid")
        void shouldRejectNullAtomGid() {
            assertThrows(IllegalArgumentException.class, () -> {
                new IdbResetAndSendFdoBuilder(TEST_OBJ_TYPE, null, 4, TEST_DATA);
            });
        }

        @Test
        @DisplayName("Should reject null data")
        void shouldRejectNullData() {
            assertThrows(IllegalArgumentException.class, () -> {
                new IdbResetAndSendFdoBuilder(TEST_OBJ_TYPE, TEST_ATOM_GID, 4, null);
            });
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> {
                new IdbResetAndSendFdoBuilder(null);
            });
        }
    }
}
