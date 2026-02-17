/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

/**
 * Configuration for ImageViewerFdoBuilder.
 *
 * <p>Encapsulates all parameters needed to generate an image viewer FDO window.
 * Used by ImageViewerKeywordHandler.</p>
 *
 * @param artId Art ID to display (e.g., "1-0-21029" or "32-5446")
 * @param title Window title text
 * @param windowWidth Total window width in pixels
 * @param windowHeight Total window height in pixels
 * @param imageX Image X position within window
 * @param imageY Image Y position within window
 */
public record ImageViewerConfig(
    String artId,
    String title,
    int windowWidth,
    int windowHeight,
    int imageX,
    int imageY
) {
    /**
     * Validate configuration parameters.
     */
    public ImageViewerConfig {
        if (artId == null || artId.isBlank()) {
            throw new IllegalArgumentException("artId cannot be null or blank");
        }
        if (title == null) {
            throw new IllegalArgumentException("title cannot be null");
        }
        if (windowWidth <= 0) {
            throw new IllegalArgumentException("windowWidth must be positive");
        }
        if (windowHeight <= 0) {
            throw new IllegalArgumentException("windowHeight must be positive");
        }
        if (imageX < 0) {
            throw new IllegalArgumentException("imageX cannot be negative");
        }
        if (imageY < 0) {
            throw new IllegalArgumentException("imageY cannot be negative");
        }
    }

    /**
     * Create a config with the art ID used as the title.
     *
     * @param artId Art ID (also used as title)
     * @param windowWidth Total window width
     * @param windowHeight Total window height
     * @param imageX Image X position
     * @param imageY Image Y position
     * @return New config instance
     */
    public static ImageViewerConfig withArtIdAsTitle(
            String artId, int windowWidth, int windowHeight, int imageX, int imageY) {
        return new ImageViewerConfig(artId, artId, windowWidth, windowHeight, imageX, imageY);
    }
}
