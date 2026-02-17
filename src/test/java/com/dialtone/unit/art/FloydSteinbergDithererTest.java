/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.art;

import com.dialtone.art.*;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FloydSteinbergDitherer.
 */
class FloydSteinbergDithererTest {

    @Test
    void shouldDitherImage() {
        // Create test image with gradient
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int x = 0; x < 32; x++) {
            int gray = x * 8;
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(x, 0, 1, 32);
        }
        g.dispose();

        // Create simple 2-color palette (black and white)
        int[] palette = new int[256];
        palette[0] = 0x000000;  // Black
        palette[1] = 0xFFFFFF;  // White
        for (int i = 2; i < 256; i++) {
            palette[i] = 0x808080;  // Gray
        }

        FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
        BufferedImage dithered = ditherer.dither(image, palette);

        assertNotNull(dithered, "Dithered image should not be null");
        assertEquals(32, dithered.getWidth(), "Width should be preserved");
        assertEquals(32, dithered.getHeight(), "Height should be preserved");
    }

    @Test
    void shouldModifyImageInPlace() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 16, 16);
        g.dispose();

        // Simple palette with limited colors
        int[] palette = new int[256];
        palette[0] = 0x000000;  // Black
        palette[1] = 0xFFFFFF;  // White
        palette[2] = 0xFF0000;  // Red  (far from gray)
        for (int i = 3; i < 256; i++) {
            palette[i] = 0x000000;  // Rest black
        }

        FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
        BufferedImage result = ditherer.dither(image, palette);

        // Should return same instance (modified in place)
        assertSame(image, result, "Should return same image instance");

        // Pixel should be quantized to a palette color
        int ditheredPixel = result.getRGB(0, 0) & 0xFFFFFF;  // Mask out alpha
        boolean isInPalette = false;
        for (int paletteColor : palette) {
            if (ditheredPixel == paletteColor) {
                isInPalette = true;
                break;
            }
        }
        assertTrue(isInPalette, "Pixel should be from the palette");
    }

    @Test
    void shouldQuantizeToNearestPaletteColor() {
        // Create image with a specific color not in palette
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x7F7F7F);  // Mid-gray

        // Create palette with only black and white
        int[] palette = new int[256];
        palette[0] = 0x000000;  // Black
        palette[1] = 0xFFFFFF;  // White
        for (int i = 2; i < 256; i++) {
            palette[i] = 0x000000;  // Fill with black
        }

        FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
        ditherer.dither(image, palette);

        int result = image.getRGB(0, 0) & 0xFFFFFF;  // Mask out alpha channel

        // Should be quantized to either black or white
        assertTrue(result == 0x000000 || result == 0xFFFFFF,
            String.format("Result 0x%06X should be either black or white", result));
    }

    @Test
    void shouldHandleSmallImage() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, 2, 2);
        g.dispose();

        int[] palette = new int[256];
        for (int i = 0; i < 256; i++) {
            palette[i] = (i << 16) | (i << 8) | i;
        }

        FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
        BufferedImage result = ditherer.dither(image, palette);

        assertNotNull(result);
        assertEquals(2, result.getWidth());
        assertEquals(2, result.getHeight());
    }

    @Test
    void shouldUseAllPaletteColors() {
        // Create colorful image
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 32, 32);
        g.setColor(Color.GREEN);
        g.fillRect(32, 0, 32, 32);
        g.setColor(Color.BLUE);
        g.fillRect(0, 32, 32, 32);
        g.setColor(Color.YELLOW);
        g.fillRect(32, 32, 32, 32);
        g.dispose();

        // Generate palette using MedianCut
        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        FloydSteinbergDitherer ditherer = new FloydSteinbergDitherer();
        BufferedImage dithered = ditherer.dither(image, palette);

        // Check that dithered image uses palette colors
        boolean foundNonPaletteColor = false;
        for (int y = 0; y < dithered.getHeight(); y++) {
            for (int x = 0; x < dithered.getWidth(); x++) {
                int pixel = dithered.getRGB(x, y) & 0xFFFFFF;  // Mask out alpha
                boolean inPalette = false;
                for (int paletteColor : palette) {
                    if (pixel == paletteColor) {
                        inPalette = true;
                        break;
                    }
                }
                if (!inPalette) {
                    foundNonPaletteColor = true;
                    break;
                }
            }
            if (foundNonPaletteColor) break;
        }

        assertFalse(foundNonPaletteColor,
            "All pixels in dithered image should be from the palette");
    }
}
