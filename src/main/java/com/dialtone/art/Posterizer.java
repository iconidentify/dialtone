/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.dialtone.utils.LoggerUtil;

import java.awt.image.BufferedImage;

/**
 * Posterization (color reduction) processor.
 * Reduces the number of color levels per channel before quantization,
 * creating a crisp, bold look typical of classic AOL graphics.
 *
 * Example: 32 levels reduces 256 shades per channel to 32 shades,
 * creating more distinct color regions and less subtle gradients.
 */
public class Posterizer {

    /**
     * Apply posterization to an image.
     *
     * @param image The source image
     * @param levels Number of levels per channel (e.g., 32 = 5-bit color)
     * @return A new posterized image
     */
    public BufferedImage posterize(BufferedImage image, int levels) {
        if (levels <= 0 || levels > 256) {
            throw new IllegalArgumentException("Levels must be between 1 and 256");
        }

        LoggerUtil.debug(() -> String.format(
            "Posterizing image to %d levels per channel", levels));

        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage posterized = new BufferedImage(
            width, height, BufferedImage.TYPE_INT_RGB);

        // Calculate posterization factor
        // For 32 levels: factor = 256 / 32 = 8
        int factor = 256 / levels;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                // Extract RGB components
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Posterize each channel:
                // 1. Divide by factor to reduce range
                // 2. Multiply back to restore scale
                // Example: r=180 with factor=8 → (180/8)*8 = 22*8 = 176
                int posterR = (r / factor) * factor;
                int posterG = (g / factor) * factor;
                int posterB = (b / factor) * factor;

                // Recombine into RGB
                int posterRgb = (posterR << 16) | (posterG << 8) | posterB;
                posterized.setRGB(x, y, posterRgb);
            }
        }

        LoggerUtil.debug(() -> "Posterization complete");
        return posterized;
    }

    /**
     * Calculate recommended posterization levels based on image characteristics.
     * This provides intelligent defaults for different art styles.
     *
     * @param image The source image
     * @param style The desired style: "crisp", "smooth", or "auto"
     * @return Recommended levels (16-64)
     */
    public int getRecommendedLevels(BufferedImage image, String style) {
        switch (style.toLowerCase()) {
            case "crisp":
                return 16;  // Very bold, cartoon-like (4-bit color)
            case "smooth":
                return 64;  // Subtle posterization (6-bit color)
            case "auto":
            default:
                // Analyze image to determine best level
                return analyzeImageComplexity(image);
        }
    }

    /**
     * Analyze image complexity to determine optimal posterization level.
     * More complex images (photos) benefit from more levels,
     * simpler images (icons) work well with fewer levels.
     */
    private int analyzeImageComplexity(BufferedImage image) {
        // Sample 10% of pixels to estimate color diversity
        int width = image.getWidth();
        int height = image.getHeight();
        int sampleCount = 0;
        long colorSum = 0;

        for (int y = 0; y < height; y += 10) {
            for (int x = 0; x < width; x += 10) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Measure local color variation
                if (x + 10 < width && y + 10 < height) {
                    int nextRgb = image.getRGB(x + 10, y + 10);
                    int nextR = (nextRgb >> 16) & 0xFF;
                    int nextG = (nextRgb >> 8) & 0xFF;
                    int nextB = nextRgb & 0xFF;

                    int variation = Math.abs(r - nextR) +
                                  Math.abs(g - nextG) +
                                  Math.abs(b - nextB);
                    colorSum += variation;
                    sampleCount++;
                }
            }
        }

        if (sampleCount == 0) return 32;  // Default

        // Calculate average variation
        double avgVariation = (double) colorSum / sampleCount;

        // Map variation to posterization levels
        // Low variation (flat colors) → fewer levels (16-24)
        // High variation (complex) → more levels (32-64)
        if (avgVariation < 20) {
            return 16;  // Very flat, icon-like
        } else if (avgVariation < 50) {
            return 24;  // Moderate complexity
        } else if (avgVariation < 100) {
            return 32;  // Standard
        } else {
            return 48;  // Complex, photo-like
        }
    }
}
