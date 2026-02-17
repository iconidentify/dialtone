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
 * Tests for MedianCutQuantizer.
 */
class MedianCutQuantizerTest {

    @Test
    void shouldGenerateFullPalette() {
        // Create test image with various colors
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

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        assertNotNull(palette, "Palette should not be null");
        assertEquals(256, palette.length, "Palette should have exactly 256 colors");
    }

    @Test
    void shouldReserveIndexZeroAsTransparent() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 32);
        g.dispose();

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        assertEquals(0x000000, palette[0], "Index 0 should be black (transparent by AOL convention)");
    }

    @Test
    void shouldAdaptToImageColors() {
        // Create predominantly red image
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 0, 0));
        g.fillRect(0, 0, 60, 60);
        g.setColor(new Color(200, 0, 0));
        g.fillRect(0, 0, 30, 30);
        g.dispose();

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        // Count red-ish colors in palette (should have some red bias)
        int redishCount = 0;
        for (int color : palette) {
            int r = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            // More lenient check: red component should be dominant
            if (r > green && r > b && r > 50) {
                redishCount++;
            }
        }

        assertTrue(redishCount >= 2,
            String.format("Palette should have red bias (found %d red-ish colors)", redishCount));
    }

    @Test
    void shouldHandleGrayscaleImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 32; i++) {
            int gray = i * 8;
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(i, 0, 1, 32);
        }
        g.dispose();

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        assertNotNull(palette);
        assertEquals(256, palette.length);
    }

    @Test
    void shouldHandleSingleColorImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, 32, 32);
        g.dispose();

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] palette = quantizer.generatePalette(image);

        assertNotNull(palette);
        assertEquals(256, palette.length);

        // First entry (after transparent black) should be close to cyan
        int cyan = palette[1];
        int r = (cyan >> 16) & 0xFF;
        int green = (cyan >> 8) & 0xFF;
        int b = cyan & 0xFF;

        assertTrue(green > 200 && b > 200 && r < 50,
            String.format("Expected cyan-ish color, got R=%d G=%d B=%d", r, green, b));
    }

    @Test
    void shouldGenerateDifferentPalettesForDifferentImages() {
        // Red image
        BufferedImage redImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D gRed = redImage.createGraphics();
        gRed.setColor(Color.RED);
        gRed.fillRect(0, 0, 32, 32);
        gRed.dispose();

        // Blue image
        BufferedImage blueImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D gBlue = blueImage.createGraphics();
        gBlue.setColor(Color.BLUE);
        gBlue.fillRect(0, 0, 32, 32);
        gBlue.dispose();

        MedianCutQuantizer quantizer = new MedianCutQuantizer();
        int[] redPalette = quantizer.generatePalette(redImage);
        int[] bluePalette = quantizer.generatePalette(blueImage);

        // Palettes should differ (not identical)
        boolean identical = true;
        for (int i = 0; i < 256; i++) {
            if (redPalette[i] != bluePalette[i]) {
                identical = false;
                break;
            }
        }

        assertFalse(identical, "Red and blue image palettes should differ");
    }
}
