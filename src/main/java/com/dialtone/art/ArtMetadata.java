/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for art assets, loaded from JSON files.
 * Describes default rendering properties for images.
 */
public class ArtMetadata {

    @JsonProperty("transparency")
    private boolean transparency;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    /**
     * Flag byte 1 (byte 36 in AOL art header) - Resource Type.
     *
     * Controls resource classification:
     * - 0x80 (Standard/Legacy): 58% of samples, lower transparency usage
     * - 0x00 (Modern/Advanced): 26% of samples, higher transparency usage
     *
     * If not specified, automatically calculated based on transparency.
     */
    @JsonProperty("flagByte1")
    private Integer flagByte1;

    /**
     * Flag byte 2 (byte 37 in AOL art header) - Size Category.
     *
     * Controls size classification based on dimensions:
     * - 0x2A (42): Tiny icons (16×16, ~256px)
     * - 0x1E (30): Small buttons (26×21, ~546px)
     * - 0x55 (85): Square icons (32×32, ~1024px)
     * - 0xE3 (227): Standard icons (19×16, ~304px)
     * - 0x00 (0): Large graphics (~4858px)
     * - 0x0F (15): XL banners (130×64, ~8320px)
     * - 0xFD (253): DEFAULT (any size)
     *
     * If not specified, automatically calculated based on width × height.
     */
    @JsonProperty("flagByte2")
    private Integer flagByte2;

    /**
     * Enable Floyd-Steinberg dithering for better detail preservation.
     * Spreads quantization errors to neighboring pixels.
     *
     * Default: true (recommended for most images)
     */
    @JsonProperty("enableDithering")
    private Boolean enableDithering;

    /**
     * Enable posterization (color reduction) before quantization.
     * Creates crisp, bold look typical of classic AOL graphics.
     *
     * Default: false (use adaptive palette without posterization)
     */
    @JsonProperty("enablePosterization")
    private Boolean enablePosterization;

    /**
     * Number of color levels per channel when posterization is enabled.
     * Lower values = more posterized (bolder, less gradients)
     * Higher values = more subtle posterization
     *
     * Recommended values:
     * - 16: Very crisp, cartoon-like (4-bit color)
     * - 32: Standard AOL-style (5-bit color)
     * - 48: Moderate posterization
     * - 64: Subtle posterization (6-bit color)
     *
     * Default: 32
     */
    @JsonProperty("posterizationLevel")
    private Integer posterizationLevel;

    public ArtMetadata() {
        // Default constructor for Jackson
    }

    public ArtMetadata(boolean transparency, int width, int height) {
        this.transparency = transparency;
        this.width = width;
        this.height = height;
    }

    public ArtMetadata(boolean transparency, int width, int height, Integer flagByte1, Integer flagByte2) {
        this.transparency = transparency;
        this.width = width;
        this.height = height;
        this.flagByte1 = flagByte1;
        this.flagByte2 = flagByte2;
    }

    public boolean isTransparency() {
        return transparency;
    }

    public void setTransparency(boolean transparency) {
        this.transparency = transparency;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Get flag byte 1 (resource type).
     * If not explicitly set in JSON, intelligently calculates based on transparency.
     *
     * @return Flag byte 1 value (byte 36 in AOL header)
     */
    public int getFlagByte1() {
        if (flagByte1 != null) {
            // JSON override always wins
            return flagByte1;
        }

        // Intelligent default based on transparency
        return AolArtFlagCalculator.calculateResourceTypeFlag(transparency);
    }

    public void setFlagByte1(Integer flagByte1) {
        this.flagByte1 = flagByte1;
    }

    /**
     * Get flag byte 2 (size category).
     * If not explicitly set in JSON, intelligently calculates based on dimensions.
     *
     * @return Flag byte 2 value (byte 37 in AOL header)
     */
    public int getFlagByte2() {
        if (flagByte2 != null) {
            // JSON override always wins
            return flagByte2;
        }

        // Intelligent default based on image dimensions
        return AolArtFlagCalculator.calculateSizeCategoryFlag(width, height);
    }

    public void setFlagByte2(Integer flagByte2) {
        this.flagByte2 = flagByte2;
    }

    /**
     * Get dithering enabled flag with intelligent default.
     *
     * @return true if dithering should be enabled (default: true)
     */
    public boolean isEnableDithering() {
        return enableDithering != null ? enableDithering : true;  // Default: enabled
    }

    public void setEnableDithering(Boolean enableDithering) {
        this.enableDithering = enableDithering;
    }

    /**
     * Get posterization enabled flag with intelligent default.
     *
     * @return true if posterization should be enabled (default: false)
     */
    public boolean isEnablePosterization() {
        return enablePosterization != null ? enablePosterization : false;  // Default: disabled
    }

    public void setEnablePosterization(Boolean enablePosterization) {
        this.enablePosterization = enablePosterization;
    }

    /**
     * Get posterization level with intelligent default.
     *
     * @return Number of levels per channel (default: 32)
     */
    public int getPosterizationLevel() {
        return posterizationLevel != null ? posterizationLevel : 32;  // Default: 5-bit color
    }

    public void setPosterizationLevel(Integer posterizationLevel) {
        this.posterizationLevel = posterizationLevel;
    }

    @Override
    public String toString() {
        return String.format(
            "ArtMetadata{transparency=%s, width=%d, height=%d, flagByte1=0x%02X, flagByte2=0x%02X, " +
            "dithering=%s, posterization=%s (level=%d)}",
            transparency, width, height, getFlagByte1(), getFlagByte2(),
            isEnableDithering(), isEnablePosterization(), getPosterizationLevel());
    }
}
