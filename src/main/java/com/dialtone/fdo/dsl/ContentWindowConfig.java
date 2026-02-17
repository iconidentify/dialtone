/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

/**
 * Configuration for content window FDO builders.
 *
 * <p>Encapsulates all parameters needed to generate a content window FDO.
 * Used by keyword handlers that display scrollable content with optional logos.</p>
 *
 * <p>This configuration enables the reusable keyword handler pattern where multiple
 * keywords (e.g., "pieter", "osxdaily") can share common FDO generation logic while
 * customizing the window title, art assets, and content.</p>
 *
 * @param keyword Keyword that triggers this window (e.g., "pieter", "osxdaily")
 * @param windowTitle Window title bar text
 * @param backgroundArtId Art GID for tiled background (e.g., "1-69-27256")
 * @param logoArtId Optional logo art GID displayed in window, null for no logo
 * @param windowWidth Total window width in pixels
 * @param windowHeight Total window height in pixels
 * @param buttonTheme Optional button color theme, null for default
 */
public record ContentWindowConfig(
    String keyword,
    String windowTitle,
    String backgroundArtId,
    String logoArtId,
    int windowWidth,
    int windowHeight,
    ButtonTheme buttonTheme
) {
    /** Default window dimensions matching welcome window */
    public static final int DEFAULT_WIDTH = 518;
    public static final int DEFAULT_HEIGHT = 300;

    /** Default background art (welcome window background) */
    public static final String DEFAULT_BACKGROUND_ART = "1-69-27256";

    /**
     * Validate configuration parameters.
     */
    public ContentWindowConfig {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword cannot be null or blank");
        }
        if (windowTitle == null || windowTitle.isBlank()) {
            throw new IllegalArgumentException("windowTitle cannot be null or blank");
        }
        if (windowWidth <= 0) {
            throw new IllegalArgumentException("windowWidth must be positive");
        }
        if (windowHeight <= 0) {
            throw new IllegalArgumentException("windowHeight must be positive");
        }
        // backgroundArtId can be null (no background)
        // logoArtId can be null (no logo)
        // buttonTheme can be null (use default)
    }

    /**
     * Create a config with default dimensions and background.
     *
     * @param keyword Keyword trigger
     * @param windowTitle Window title
     * @param logoArtId Optional logo art GID
     * @return New config with default dimensions
     */
    public static ContentWindowConfig withDefaults(String keyword, String windowTitle, String logoArtId) {
        return new ContentWindowConfig(
            keyword,
            windowTitle,
            DEFAULT_BACKGROUND_ART,
            logoArtId,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            ButtonTheme.DEFAULT
        );
    }

    /**
     * Create a config with default dimensions, background, and no logo.
     *
     * @param keyword Keyword trigger
     * @param windowTitle Window title
     * @return New config with defaults and no logo
     */
    public static ContentWindowConfig withDefaultsNoLogo(String keyword, String windowTitle) {
        return withDefaults(keyword, windowTitle, null);
    }

    /**
     * Returns the button theme, falling back to default if null.
     *
     * @return Non-null button theme
     */
    public ButtonTheme effectiveButtonTheme() {
        return buttonTheme != null ? buttonTheme : ButtonTheme.DEFAULT;
    }
}
