/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.dialtone.utils.LoggerUtil;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Median Cut color quantization algorithm.
 * Generates an adaptive 256-color palette based on the actual colors in the image,
 * producing much better quality than a static web-safe palette.
 *
 * Algorithm:
 * 1. Collect all unique colors from the image
 * 2. Place them in a "color box" (3D RGB space)
 * 3. Repeatedly split the box with the widest color range along its median
 * 4. Continue until we have 256 boxes
 * 5. Each box's average color becomes a palette entry
 */
public class MedianCutQuantizer {

    /**
     * Generate a 256-color palette using Median Cut algorithm.
     *
     * @param image The source image
     * @return Array of 256 RGB colors [index] = 0xRRGGBB
     */
    public int[] generatePalette(BufferedImage image) {
        LoggerUtil.debug(() -> "Generating adaptive palette using Median Cut algorithm");

        // Step 1: Collect all unique colors from the image
        Map<Integer, Integer> colorCounts = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;  // Strip alpha
                colorCounts.merge(rgb, 1, Integer::sum);
            }
        }

        LoggerUtil.debug(() -> String.format("Found %d unique colors in image", colorCounts.size()));

        // Step 2: Convert to list of color entries
        List<ColorEntry> colors = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
            colors.add(new ColorEntry(entry.getKey(), entry.getValue()));
        }

        // Step 3: Start with one box containing all colors
        PriorityQueue<ColorBox> boxes = new PriorityQueue<>((a, b) ->
            Integer.compare(b.getRange(), a.getRange()));  // Largest range first
        boxes.add(new ColorBox(colors));

        // Step 4: Split boxes until we have 256 (or can't split anymore)
        while (boxes.size() < 256) {
            ColorBox box = boxes.poll();
            if (box == null || box.colors.size() <= 1) {
                if (box != null) boxes.add(box);
                break;  // Can't split further
            }

            ColorBox[] split = box.split();
            if (split != null) {
                boxes.add(split[0]);
                boxes.add(split[1]);
            } else {
                boxes.add(box);  // Couldn't split, put it back
                break;
            }
        }

        // Step 5: Generate palette from box averages
        int[] palette = new int[256];
        int index = 0;

        // Reserve index 0 for transparency (black)
        palette[0] = 0x000000;
        index++;

        // Fill palette with box averages
        for (ColorBox box : boxes) {
            if (index >= 256) break;
            palette[index++] = box.getAverageColor();
        }

        // Fill remaining slots with grayscale if needed
        while (index < 256) {
            int gray = (index - boxes.size()) * 255 / (256 - boxes.size());
            palette[index++] = (gray << 16) | (gray << 8) | gray;
        }

        LoggerUtil.debug(() -> String.format(
            "Generated %d-color palette from %d color boxes",
            256, boxes.size()));

        return palette;
    }

    /**
     * Represents a single color with its frequency.
     */
    private static class ColorEntry {
        final int rgb;      // 0xRRGGBB
        final int count;    // Frequency in image

        ColorEntry(int rgb, int count) {
            this.rgb = rgb;
            this.count = count;
        }

        int getRed() { return (rgb >> 16) & 0xFF; }
        int getGreen() { return (rgb >> 8) & 0xFF; }
        int getBlue() { return rgb & 0xFF; }
    }

    /**
     * Represents a box of colors in RGB space.
     */
    private static class ColorBox {
        final List<ColorEntry> colors;
        private int minRed, maxRed;
        private int minGreen, maxGreen;
        private int minBlue, maxBlue;

        ColorBox(List<ColorEntry> colors) {
            this.colors = colors;
            calculateBounds();
        }

        private void calculateBounds() {
            minRed = minGreen = minBlue = 255;
            maxRed = maxGreen = maxBlue = 0;

            for (ColorEntry c : colors) {
                minRed = Math.min(minRed, c.getRed());
                maxRed = Math.max(maxRed, c.getRed());
                minGreen = Math.min(minGreen, c.getGreen());
                maxGreen = Math.max(maxGreen, c.getGreen());
                minBlue = Math.min(minBlue, c.getBlue());
                maxBlue = Math.max(maxBlue, c.getBlue());
            }
        }

        /**
         * Get the maximum range across all color channels.
         */
        int getRange() {
            int redRange = maxRed - minRed;
            int greenRange = maxGreen - minGreen;
            int blueRange = maxBlue - minBlue;
            return Math.max(redRange, Math.max(greenRange, blueRange));
        }

        /**
         * Split this box along the median of its widest dimension.
         */
        ColorBox[] split() {
            if (colors.size() <= 1) return null;

            // Find widest dimension
            int redRange = maxRed - minRed;
            int greenRange = maxGreen - minGreen;
            int blueRange = maxBlue - minBlue;

            // Sort by widest dimension
            if (redRange >= greenRange && redRange >= blueRange) {
                colors.sort(Comparator.comparingInt(ColorEntry::getRed));
            } else if (greenRange >= blueRange) {
                colors.sort(Comparator.comparingInt(ColorEntry::getGreen));
            } else {
                colors.sort(Comparator.comparingInt(ColorEntry::getBlue));
            }

            // Split at median (weighted by pixel count for better quality)
            int totalCount = colors.stream().mapToInt(c -> c.count).sum();
            int halfCount = totalCount / 2;

            int splitIndex = 0;
            int currentCount = 0;
            for (int i = 0; i < colors.size(); i++) {
                currentCount += colors.get(i).count;
                if (currentCount >= halfCount) {
                    splitIndex = i + 1;
                    break;
                }
            }

            // Ensure split is valid
            if (splitIndex == 0) splitIndex = 1;
            if (splitIndex >= colors.size()) splitIndex = colors.size() - 1;

            List<ColorEntry> left = new ArrayList<>(colors.subList(0, splitIndex));
            List<ColorEntry> right = new ArrayList<>(colors.subList(splitIndex, colors.size()));

            return new ColorBox[] {
                new ColorBox(left),
                new ColorBox(right)
            };
        }

        /**
         * Calculate the average color of all colors in this box,
         * weighted by pixel count.
         */
        int getAverageColor() {
            long totalCount = 0;
            long sumRed = 0, sumGreen = 0, sumBlue = 0;

            for (ColorEntry c : colors) {
                sumRed += (long) c.getRed() * c.count;
                sumGreen += (long) c.getGreen() * c.count;
                sumBlue += (long) c.getBlue() * c.count;
                totalCount += c.count;
            }

            if (totalCount == 0) return 0;

            int avgRed = (int) (sumRed / totalCount);
            int avgGreen = (int) (sumGreen / totalCount);
            int avgBlue = (int) (sumBlue / totalCount);

            return (avgRed << 16) | (avgGreen << 8) | avgBlue;
        }
    }
}
