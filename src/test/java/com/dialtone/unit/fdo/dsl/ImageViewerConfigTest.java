/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl;

import com.dialtone.fdo.dsl.ImageViewerConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageViewerConfig")
class ImageViewerConfigTest {

    private static final String VALID_ART_ID = "1-0-21029";
    private static final String VALID_TITLE = "Test Image";
    private static final int VALID_WIDTH = 400;
    private static final int VALID_HEIGHT = 300;
    private static final int VALID_X = 20;
    private static final int VALID_Y = 20;

    @Nested
    @DisplayName("Valid configuration")
    class ValidConfiguration {

        @Test
        @DisplayName("Should create config with valid parameters")
        void shouldCreateConfigWithValidParameters() {
            ImageViewerConfig config = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals(VALID_ART_ID, config.artId());
            assertEquals(VALID_TITLE, config.title());
            assertEquals(VALID_WIDTH, config.windowWidth());
            assertEquals(VALID_HEIGHT, config.windowHeight());
            assertEquals(VALID_X, config.imageX());
            assertEquals(VALID_Y, config.imageY());
        }

        @Test
        @DisplayName("Should allow zero for image position")
        void shouldAllowZeroForImagePosition() {
            ImageViewerConfig config = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, 0, 0);

            assertEquals(0, config.imageX());
            assertEquals(0, config.imageY());
        }

        @Test
        @DisplayName("Should allow empty title")
        void shouldAllowEmptyTitle() {
            ImageViewerConfig config = new ImageViewerConfig(
                VALID_ART_ID, "", VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals("", config.title());
        }

        @Test
        @DisplayName("Should support two-part art ID")
        void shouldSupportTwoPartArtId() {
            ImageViewerConfig config = new ImageViewerConfig(
                "32-5446", VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals("32-5446", config.artId());
        }

        @Test
        @DisplayName("Should support three-part art ID")
        void shouldSupportThreePartArtId() {
            ImageViewerConfig config = new ImageViewerConfig(
                "1-69-27256", VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals("1-69-27256", config.artId());
        }
    }

    @Nested
    @DisplayName("Invalid configuration")
    class InvalidConfiguration {

        @Test
        @DisplayName("Should throw exception for null artId")
        void shouldThrowExceptionForNullArtId() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(null, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for blank artId")
        void shouldThrowExceptionForBlankArtId() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig("  ", VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for empty artId")
        void shouldThrowExceptionForEmptyArtId() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig("", VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for null title")
        void shouldThrowExceptionForNullTitle() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, null, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for zero windowWidth")
        void shouldThrowExceptionForZeroWindowWidth() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, 0, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for negative windowWidth")
        void shouldThrowExceptionForNegativeWindowWidth() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, -100, VALID_HEIGHT, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for zero windowHeight")
        void shouldThrowExceptionForZeroWindowHeight() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, VALID_WIDTH, 0, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for negative windowHeight")
        void shouldThrowExceptionForNegativeWindowHeight() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, VALID_WIDTH, -100, VALID_X, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for negative imageX")
        void shouldThrowExceptionForNegativeImageX() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, -1, VALID_Y));
        }

        @Test
        @DisplayName("Should throw exception for negative imageY")
        void shouldThrowExceptionForNegativeImageY() {
            assertThrows(IllegalArgumentException.class, () ->
                new ImageViewerConfig(VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, -1));
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("withArtIdAsTitle should use artId as title")
        void withArtIdAsTitleShouldUseArtIdAsTitle() {
            ImageViewerConfig config = ImageViewerConfig.withArtIdAsTitle(
                "32-5446", VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals("32-5446", config.artId());
            assertEquals("32-5446", config.title());
        }
    }

    @Nested
    @DisplayName("Record behavior")
    class RecordBehavior {

        @Test
        @DisplayName("Should support equality for identical values")
        void shouldSupportEquality() {
            ImageViewerConfig config1 = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);
            ImageViewerConfig config2 = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            ImageViewerConfig config1 = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);
            ImageViewerConfig config2 = new ImageViewerConfig(
                "different-id", VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            ImageViewerConfig config = new ImageViewerConfig(
                VALID_ART_ID, VALID_TITLE, VALID_WIDTH, VALID_HEIGHT, VALID_X, VALID_Y);

            String str = config.toString();
            assertTrue(str.contains(VALID_ART_ID));
            assertTrue(str.contains(VALID_TITLE));
        }
    }
}
