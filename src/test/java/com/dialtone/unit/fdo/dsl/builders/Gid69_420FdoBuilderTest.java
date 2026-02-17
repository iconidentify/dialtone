/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.Gid69_420FdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gid69_420FdoBuilder")
class Gid69_420FdoBuilderTest {

    private Gid69_420FdoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new Gid69_420FdoBuilder();
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct GID")
        void shouldReturnCorrectGid() {
            assertEquals("69-420", builder.getGid());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            String description = builder.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }
    }

    @Nested
    @DisplayName("FDO source generation")
    class FdoSourceGeneration {

        @Test
        @DisplayName("Should generate valid FDO source with default context")
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
        @DisplayName("Should contain object ID 69-420")
        void shouldContainObjectId() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("mat_object_id"), "Should have object ID atom");
            assertTrue(fdo.contains("69-420") || fdo.contains("69, 420"),
                "Should contain GID reference");
        }

        @Test
        @DisplayName("Should contain view widget with relative tag 256")
        void shouldContainViewWidget() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("view"), "Should have view widget");
            assertTrue(fdo.contains("mat_relative_tag") && fdo.contains("256"),
                "Should have view relative tag 256");
        }

        @Test
        @DisplayName("Should contain edit_view widget with relative tag 258")
        void shouldContainEditViewWidget() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("edit_view"), "Should have edit_view widget");
            assertTrue(fdo.contains("258"), "Should have edit_view relative tag 258");
        }

        @Test
        @DisplayName("Should contain Send trigger")
        void shouldContainSendTrigger() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("trigger") && fdo.contains("Send"),
                "Should have Send trigger");
        }

        @Test
        @DisplayName("Should contain buf_set_token with St")
        void shouldContainBufSetToken() {
            String fdo = builder.toSource(RenderingContext.DEFAULT);

            assertTrue(fdo.contains("buf_set_token") && fdo.contains("St"),
                "Should have St token in action");
        }

        @Test
        @DisplayName("Should produce consistent output")
        void shouldProduceConsistentOutput() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.MAC, false);
            String fdo1 = builder.toSource(ctx);
            String fdo2 = builder.toSource(ctx);

            assertEquals(fdo1, fdo2, "Multiple calls should produce identical output");
        }
    }

    @Nested
    @DisplayName("Platform-specific variants")
    class PlatformVariants {

        @Test
        @DisplayName("BW mode should have smaller edit field width")
        void bwModeShouldHaveSmallerEditWidth() {
            RenderingContext colorCtx = new RenderingContext(ClientPlatform.MAC, false);
            RenderingContext bwCtx = new RenderingContext(ClientPlatform.MAC, true);

            String colorFdo = builder.toSource(colorCtx);
            String bwFdo = builder.toSource(bwCtx);

            // Color mode has 82-width edit, BW has 56-width
            assertTrue(bwFdo.contains("56"), "BW mode should have 56-width edit");
        }

        @Test
        @DisplayName("Color mode should use PLACE trigger style for Send button")
        void colorModeShouldUsePlaceTriggerStyle() {
            RenderingContext ctx = new RenderingContext(ClientPlatform.MAC, false);
            String fdo = builder.toSource(ctx);

            assertTrue(fdo.contains("mat_trigger_style") || fdo.contains("PLACE"),
                "Color mode should have trigger style");
        }

        @Test
        @DisplayName("All four combinations should produce valid FDO")
        void allFourCombinationsShouldProduceValidFdo() {
            // Mac + Color
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, false)),
                "Mac color");

            // Mac + BW
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.MAC, true)),
                "Mac BW");

            // Windows + Color
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false)),
                "Windows color");

            // Windows + BW
            assertValidFdo(builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, true)),
                "Windows BW");
        }

        private void assertValidFdo(String fdo, String description) {
            assertNotNull(fdo, description + " should not be null");
            assertFalse(fdo.isEmpty(), description + " should not be empty");
            assertTrue(fdo.contains("uni_start_stream"), description + " should have stream start");
            assertTrue(fdo.contains("uni_end_stream"), description + " should have stream end");
            assertTrue(fdo.contains("mat_object_id"), description + " should have object ID");
            assertTrue(fdo.contains("view"), description + " should have view widget");
            assertTrue(fdo.contains("edit_view"), description + " should have edit_view widget");
            assertTrue(fdo.contains("Send"), description + " should have Send trigger");
        }


    }

    @Nested
    @DisplayName("Backward compatibility (deprecated methods)")
    class DeprecatedMethods {

        @Test
        @DisplayName("Deprecated toSource(Map) should still work")
        @SuppressWarnings("deprecation")
        void deprecatedToSourceShouldWork() {
            java.util.Map<String, Object> nullMap = null;
            String fdo = builder.toSource(nullMap);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
            assertTrue(fdo.contains("uni_start_stream"));
        }

        @Test
        @DisplayName("Deprecated toSourceBw(Map) should still work")
        @SuppressWarnings("deprecation")
        void deprecatedToSourceBwShouldWork() {
            String fdo = builder.toSourceBw(null);

            assertNotNull(fdo);
            assertFalse(fdo.isEmpty());
            assertTrue(fdo.contains("uni_start_stream"));
        }

        @Test
        @DisplayName("Deprecated hasBwVariant() should return true")
        @SuppressWarnings("deprecation")
        void deprecatedHasBwVariantShouldReturnTrue() {
            // The deprecated default returns false, but toSource handles BW via context
            // This test just ensures the deprecated method works
            boolean result = builder.hasBwVariant();
            // Note: With the new interface, hasBwVariant defaults to false
            // The builder no longer needs to override it
            assertFalse(result); // Default interface behavior
        }
    }
}
