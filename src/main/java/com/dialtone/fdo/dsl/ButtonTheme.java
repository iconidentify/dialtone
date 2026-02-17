/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

import java.util.Properties;

/**
 * Immutable theme configuration for FDO button styling.
 *
 * <p>Encapsulates the four color properties used by AOL 3.0 style buttons:</p>
 * <ul>
 *   <li>colorFace - main button background (RGB triplet)</li>
 *   <li>colorText - button label text (RGB triplet)</li>
 *   <li>colorTopEdge - 3D highlight edge (RGB triplet)</li>
 *   <li>colorBottomEdge - 3D shadow edge (RGB triplet)</li>
 * </ul>
 *
 * <p>Color values are stored as int arrays of 3 elements [R, G, B].</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * ButtonTheme theme = ButtonTheme.DEFAULT;
 * int[] face = theme.colorFace(); // [252, 157, 44]
 * </pre>
 */
public record ButtonTheme(
    int[] colorFace,
    int[] colorText,
    int[] colorTopEdge,
    int[] colorBottomEdge
) {
    /** Default orange/gold button theme matching application.properties defaults */
    public static final ButtonTheme DEFAULT = new ButtonTheme(
        new int[]{252, 157, 44},    // Orange face
        new int[]{0, 30, 55},       // Dark blue text
        new int[]{255, 200, 100},   // Light orange highlight
        new int[]{150, 90, 20}      // Dark orange shadow
    );

    /**
     * Create ButtonTheme from application properties.
     *
     * <p>Expected property keys:</p>
     * <ul>
     *   <li>button.color.face - e.g., "252, 157, 44"</li>
     *   <li>button.color.text - e.g., "0, 30, 55"</li>
     *   <li>button.color.top.edge - e.g., "255, 200, 100"</li>
     *   <li>button.color.bottom.edge - e.g., "150, 90, 20"</li>
     * </ul>
     *
     * @param props Properties containing button.color.* settings
     * @return ButtonTheme with values from properties or defaults for missing values
     */
    public static ButtonTheme fromProperties(Properties props) {
        if (props == null) {
            return DEFAULT;
        }
        return new ButtonTheme(
            parseRgb(props.getProperty("button.color.face"), DEFAULT.colorFace()),
            parseRgb(props.getProperty("button.color.text"), DEFAULT.colorText()),
            parseRgb(props.getProperty("button.color.top.edge"), DEFAULT.colorTopEdge()),
            parseRgb(props.getProperty("button.color.bottom.edge"), DEFAULT.colorBottomEdge())
        );
    }

    /**
     * Parse RGB string "r, g, b" to int array.
     *
     * @param rgb RGB string like "252, 157, 44"
     * @param defaultValue Default to use if parsing fails
     * @return int array [r, g, b]
     */
    private static int[] parseRgb(String rgb, int[] defaultValue) {
        if (rgb == null || rgb.isBlank()) {
            return defaultValue;
        }
        try {
            String[] parts = rgb.split(",");
            if (parts.length != 3) {
                return defaultValue;
            }
            return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
