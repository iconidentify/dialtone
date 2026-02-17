/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl;

import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.ContentWindowConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentWindowConfig")
class ContentWindowConfigTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should create config with valid parameters")
        void shouldCreateWithValidParams() {
            ContentWindowConfig config = new ContentWindowConfig(
                "test",
                "Test Window",
                "1-69-27256",
                "1-69-40001",
                518,
                300,
                ButtonTheme.DEFAULT
            );

            assertEquals("test", config.keyword());
            assertEquals("Test Window", config.windowTitle());
            assertEquals("1-69-27256", config.backgroundArtId());
            assertEquals("1-69-40001", config.logoArtId());
            assertEquals(518, config.windowWidth());
            assertEquals(300, config.windowHeight());
            assertEquals(ButtonTheme.DEFAULT, config.buttonTheme());
        }

        @Test
        @DisplayName("Should allow null logoArtId")
        void shouldAllowNullLogoArtId() {
            ContentWindowConfig config = new ContentWindowConfig(
                "test", "Test Window", "1-69-27256", null, 518, 300, null
            );

            assertNull(config.logoArtId());
        }

        @Test
        @DisplayName("Should allow null backgroundArtId")
        void shouldAllowNullBackgroundArtId() {
            ContentWindowConfig config = new ContentWindowConfig(
                "test", "Test Window", null, null, 518, 300, null
            );

            assertNull(config.backgroundArtId());
        }

        @Test
        @DisplayName("Should allow null buttonTheme")
        void shouldAllowNullButtonTheme() {
            ContentWindowConfig config = new ContentWindowConfig(
                "test", "Test Window", "1-69-27256", null, 518, 300, null
            );

            assertNull(config.buttonTheme());
        }

        @Test
        @DisplayName("Should reject null keyword")
        void shouldRejectNullKeyword() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig(null, "Title", "1-69-27256", null, 518, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject blank keyword")
        void shouldRejectBlankKeyword() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("  ", "Title", "1-69-27256", null, 518, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject null windowTitle")
        void shouldRejectNullWindowTitle() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", null, "1-69-27256", null, 518, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject blank windowTitle")
        void shouldRejectBlankWindowTitle() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", "  ", "1-69-27256", null, 518, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject zero windowWidth")
        void shouldRejectZeroWindowWidth() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", "Title", "1-69-27256", null, 0, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject negative windowWidth")
        void shouldRejectNegativeWindowWidth() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", "Title", "1-69-27256", null, -100, 300, null)
            );
        }

        @Test
        @DisplayName("Should reject zero windowHeight")
        void shouldRejectZeroWindowHeight() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", "Title", "1-69-27256", null, 518, 0, null)
            );
        }

        @Test
        @DisplayName("Should reject negative windowHeight")
        void shouldRejectNegativeWindowHeight() {
            assertThrows(IllegalArgumentException.class, () ->
                new ContentWindowConfig("test", "Title", "1-69-27256", null, 518, -100, null)
            );
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("withDefaults should use default dimensions and background")
        void withDefaultsShouldUseDefaults() {
            ContentWindowConfig config = ContentWindowConfig.withDefaults(
                "pieter", "Pieter Retro", "1-69-40001"
            );

            assertEquals("pieter", config.keyword());
            assertEquals("Pieter Retro", config.windowTitle());
            assertEquals(ContentWindowConfig.DEFAULT_BACKGROUND_ART, config.backgroundArtId());
            assertEquals("1-69-40001", config.logoArtId());
            assertEquals(ContentWindowConfig.DEFAULT_WIDTH, config.windowWidth());
            assertEquals(ContentWindowConfig.DEFAULT_HEIGHT, config.windowHeight());
            assertEquals(ButtonTheme.DEFAULT, config.buttonTheme());
        }

        @Test
        @DisplayName("withDefaultsNoLogo should use defaults without logo")
        void withDefaultsNoLogoShouldWork() {
            ContentWindowConfig config = ContentWindowConfig.withDefaultsNoLogo(
                "test", "Test Window"
            );

            assertEquals("test", config.keyword());
            assertEquals("Test Window", config.windowTitle());
            assertNull(config.logoArtId());
            assertEquals(ContentWindowConfig.DEFAULT_WIDTH, config.windowWidth());
            assertEquals(ContentWindowConfig.DEFAULT_HEIGHT, config.windowHeight());
        }
    }

    @Nested
    @DisplayName("effectiveButtonTheme")
    class EffectiveButtonTheme {

        @Test
        @DisplayName("Should return provided theme when not null")
        void shouldReturnProvidedTheme() {
            ButtonTheme customTheme = new ButtonTheme(
                new int[]{100, 100, 100},
                new int[]{200, 200, 200},
                new int[]{50, 50, 50},
                new int[]{150, 150, 150}
            );
            ContentWindowConfig config = new ContentWindowConfig(
                "test", "Title", null, null, 518, 300, customTheme
            );

            assertSame(customTheme, config.effectiveButtonTheme());
        }

        @Test
        @DisplayName("Should return default theme when null")
        void shouldReturnDefaultWhenNull() {
            ContentWindowConfig config = new ContentWindowConfig(
                "test", "Title", null, null, 518, 300, null
            );

            assertEquals(ButtonTheme.DEFAULT, config.effectiveButtonTheme());
        }
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("Default dimensions should match welcome window")
        void defaultDimensionsShouldMatchWelcome() {
            assertEquals(518, ContentWindowConfig.DEFAULT_WIDTH);
            assertEquals(300, ContentWindowConfig.DEFAULT_HEIGHT);
        }

        @Test
        @DisplayName("Default background should be welcome background")
        void defaultBackgroundShouldBeWelcome() {
            assertEquals("1-69-27256", ContentWindowConfig.DEFAULT_BACKGROUND_ART);
        }
    }
}
