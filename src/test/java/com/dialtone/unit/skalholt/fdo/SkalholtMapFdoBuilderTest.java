/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.skalholt.fdo;

import com.dialtone.skalholt.fdo.SkalholtMapFdoBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkalholtMapFdoBuilder")
class SkalholtMapFdoBuilderTest {

    private SkalholtMapFdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SkalholtMapFdoBuilder();
    }

    @Nested
    @DisplayName("openMapWindow()")
    class OpenMapWindow {

        @Test
        @DisplayName("Should generate valid FDO source")
        void shouldGenerateValidFdoSource() {
            String fdo = builder.openMapWindow();

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("Should contain stream start and end")
        void shouldContainStreamStartAndEnd() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("uni_start_stream"), "Should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), "Should have stream end");
        }

        @Test
        @DisplayName("Should reference GID 69-421")
        void shouldReferenceGid() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("man_preset_gid") || fdo.contains("uni_invoke_no_context"),
                "Should have GID reference atoms");
            assertTrue(fdo.contains("69") && fdo.contains("421"),
                "Should contain GID 69-421 values");
        }

        @Test
        @DisplayName("Should set window title")
        void shouldSetWindowTitle() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("mat_title"), "Should have title atom");
            assertTrue(fdo.contains("Skalholt Map"), "Should have correct title text");
        }

        @Test
        @DisplayName("Should configure close action with MC token")
        void shouldConfigureCloseAction() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("act_set_criterion") || fdo.contains("CLOSE"),
                "Should configure close criterion");
            assertTrue(fdo.contains("MC"), "Should send MC token on close");
        }

        @Test
        @DisplayName("Should include display update")
        void shouldIncludeDisplayUpdate() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("man_update_display"), "Should have display update");
        }

        @Test
        @DisplayName("Should include focus command")
        void shouldIncludeFocusCommand() {
            String fdo = builder.openMapWindow();

            assertTrue(fdo.contains("man_make_focus"), "Should make window focused");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            String fdo1 = builder.openMapWindow();
            String fdo2 = builder.openMapWindow();

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("updateMapContent()")
    class UpdateMapContent {

        @Test
        @DisplayName("Should generate valid FDO source")
        void shouldGenerateValidFdoSource() {
            String fdo = builder.updateMapContent("Test map data");

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
        }

        @Test
        @DisplayName("Should contain stream start and end")
        void shouldContainStreamStartAndEnd() {
            String fdo = builder.updateMapContent("Map content");

            assertTrue(fdo.contains("uni_start_stream"), "Should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), "Should have stream end");
        }

        @Test
        @DisplayName("Should target GID 69-421")
        void shouldTargetGid() {
            String fdo = builder.updateMapContent("Map data");

            assertTrue(fdo.contains("man_set_context_global_id") || fdo.contains("man_preset_gid"),
                "Should set context to 69-421");
            assertTrue(fdo.contains("69") && fdo.contains("421"),
                "Should contain GID values");
        }

        @Test
        @DisplayName("Should target VIEW control via relative tag 256")
        void shouldTargetViewControl() {
            String fdo = builder.updateMapContent("Map data");

            assertTrue(fdo.contains("man_set_context_relative"),
                "Should set relative context");
            assertTrue(fdo.contains("256"), "Should target tag 256");
        }

        @Test
        @DisplayName("Should use replace data for content update")
        void shouldUseReplaceData() {
            String fdo = builder.updateMapContent("Map content here");

            assertTrue(fdo.contains("man_replace_data"), "Should use replace data atom");
        }

        @Test
        @DisplayName("Should include display update")
        void shouldIncludeDisplayUpdate() {
            String fdo = builder.updateMapContent("Map");

            assertTrue(fdo.contains("man_update_display"), "Should have display update");
        }

        @Test
        @DisplayName("Should convert newlines to DEL character")
        void shouldConvertNewlines() {
            String mapWithNewlines = "Line1\nLine2\nLine3";
            String fdo = builder.updateMapContent(mapWithNewlines);

            // The DEL character (0x7F) should replace newlines
            // We can't easily check for DEL in the FDO source, but we can verify
            // the original newlines are NOT present in the data section
            assertFalse(fdo.contains("Line1\nLine2"), "Newlines should be converted");
        }

        @Test
        @DisplayName("Should handle empty map data")
        void shouldHandleEmptyMapData() {
            String fdo = builder.updateMapContent("");

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
            assertTrue(fdo.contains("man_replace_data"), "Should still have replace data");
        }

        @Test
        @DisplayName("Should handle multiline map data")
        void shouldHandleMultilineMapData() {
            String mapData = """
                +---------+
                |    @    |
                |  #####  |
                |    .    |
                +---------+
                """;
            String fdo = builder.updateMapContent(mapData);

            assertNotNull(fdo);
            assertTrue(fdo.contains("man_replace_data"), "Should have replace data");
        }

        @Test
        @DisplayName("Should produce consistent output for same input")
        void shouldProduceConsistentOutput() {
            String mapData = "Consistent map data";
            String fdo1 = builder.updateMapContent(mapData);
            String fdo2 = builder.updateMapContent(mapData);

            assertEquals(fdo1, fdo2, "Same input should produce identical output");
        }

        @Test
        @DisplayName("Should produce different output for different input")
        void shouldProduceDifferentOutputForDifferentInput() {
            String fdo1 = builder.updateMapContent("Map A");
            String fdo2 = builder.updateMapContent("Map B");

            assertNotEquals(fdo1, fdo2, "Different input should produce different output");
        }

        @Test
        @DisplayName("Should target window via man_preset_gid")
        void shouldTargetWindowViaPresetGid() {
            String fdo = builder.updateMapContent("Map data");

            // FDO should use man_preset_gid to target the window
            assertTrue(fdo.contains("man_preset_gid"),
                "Should target window via man_preset_gid");
        }
    }
}
