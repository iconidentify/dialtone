/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.dialtone.utils.LoggerUtil;

import java.awt.image.BufferedImage;

/**
 * Floyd-Steinberg error diffusion dithering algorithm.
 * Spreads quantization errors to neighboring pixels, massively improving
 * perceived detail when using limited color palettes (like 256 colors).
 *
 * Error distribution pattern:
 * <pre>
 *         X   7/16
 *     3/16  5/16  1/16
 * </pre>
 *
 * Where X is the current pixel being processed.
 */
public class FloydSteinbergDitherer {

    /**
     * Apply Floyd-Steinberg dithering to an image using the given palette.
     *
     * @param image The source image (will be modified in place)
     * @param palette Array of 256 RGB colors [index] = 0xRRGGBB
     * @return The dithered image (same instance as input, modified)
     */
    public BufferedImage dither(BufferedImage image, int[] palette) {
        LoggerUtil.debug(() -> "Applying Floyd-Steinberg dithering");

        int width = image.getWidth();
        int height = image.getHeight();

        // Error buffers for current and next row
        float[][] errorRed = new float[height][width];
        float[][] errorGreen = new float[height][width];
        float[][] errorBlue = new float[height][width];

        // Process pixels left-to-right, top-to-bottom
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get original pixel color
                int rgb = image.getRGB(x, y);
                int r = ((rgb >> 16) & 0xFF);
                int g = ((rgb >> 8) & 0xFF);
                int b = (rgb & 0xFF);

                // Add accumulated error from previous pixels
                float newR = clamp(r + errorRed[y][x]);
                float newG = clamp(g + errorGreen[y][x]);
                float newB = clamp(b + errorBlue[y][x]);

                // Find closest palette color
                int paletteIndex = findClosestColor((int) newR, (int) newG, (int) newB, palette);
                int paletteColor = palette[paletteIndex];

                int palR = (paletteColor >> 16) & 0xFF;
                int palG = (paletteColor >> 8) & 0xFF;
                int palB = paletteColor & 0xFF;

                // Write quantized color to image
                image.setRGB(x, y, paletteColor);

                // Calculate quantization error
                float errR = newR - palR;
                float errG = newG - palG;
                float errB = newB - palB;

                // Distribute error to neighboring pixels (Floyd-Steinberg pattern)
                // Right pixel (x+1, y): 7/16 of error
                if (x + 1 < width) {
                    errorRed[y][x + 1] += errR * 7.0f / 16.0f;
                    errorGreen[y][x + 1] += errG * 7.0f / 16.0f;
                    errorBlue[y][x + 1] += errB * 7.0f / 16.0f;
                }

                // Below-left pixel (x-1, y+1): 3/16 of error
                if (y + 1 < height && x - 1 >= 0) {
                    errorRed[y + 1][x - 1] += errR * 3.0f / 16.0f;
                    errorGreen[y + 1][x - 1] += errG * 3.0f / 16.0f;
                    errorBlue[y + 1][x - 1] += errB * 3.0f / 16.0f;
                }

                // Below pixel (x, y+1): 5/16 of error
                if (y + 1 < height) {
                    errorRed[y + 1][x] += errR * 5.0f / 16.0f;
                    errorGreen[y + 1][x] += errG * 5.0f / 16.0f;
                    errorBlue[y + 1][x] += errB * 5.0f / 16.0f;
                }

                // Below-right pixel (x+1, y+1): 1/16 of error
                if (y + 1 < height && x + 1 < width) {
                    errorRed[y + 1][x + 1] += errR * 1.0f / 16.0f;
                    errorGreen[y + 1][x + 1] += errG * 1.0f / 16.0f;
                    errorBlue[y + 1][x + 1] += errB * 1.0f / 16.0f;
                }
            }
        }

        LoggerUtil.debug(() -> "Floyd-Steinberg dithering complete");
        return image;
    }

    /**
     * Find the closest color in the palette to the given RGB color.
     * Uses simple Euclidean distance in RGB space.
     */
    private int findClosestColor(int r, int g, int b, int[] palette) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < palette.length; i++) {
            int palR = (palette[i] >> 16) & 0xFF;
            int palG = (palette[i] >> 8) & 0xFF;
            int palB = palette[i] & 0xFF;

            // Euclidean distance in RGB space
            int dr = r - palR;
            int dg = g - palG;
            int db = b - palB;
            int distance = dr * dr + dg * dg + db * db;

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * Clamp a float value to the range [0, 255].
     */
    private float clamp(float value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }
}
