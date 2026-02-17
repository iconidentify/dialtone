/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.art;

import com.dialtone.art.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AolArtFlagCalculator.
 * Validates intelligent flag byte calculation based on image characteristics.
 */
class AolArtFlagCalculatorTest {

    /**
     * Test size category calculation - now always returns 0xFD for safety.
     * Size-specific flags can cause rendering issues in some AOL builds.
     */
    @Test
    void shouldAlwaysReturnDefaultSizeCategory() {
        // All sizes should return 0xFD (DEFAULT) for maximum compatibility
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(16, 16),
                "16×16 should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(26, 21),
                "26×21 should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(32, 32),
                "32×32 should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(42, 42),
                "42×42 should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(130, 64),
                "130×64 should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(200, 100),
                "200×100 should use DEFAULT (0xFD) for safety");
    }

    /**
     * Test that all sizes consistently return DEFAULT flag.
     * (Previously tested boundary conditions, but now we use DEFAULT for all sizes)
     */
    @Test
    void shouldConsistentlyReturnDefaultForAllSizes() {
        // All sizes consistently return 0xFD for maximum compatibility
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(1, 1));
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(17, 17));
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(100, 100));
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(500, 500));
    }

    /**
     * Test resource type flag calculation based on transparency.
     * NOTE: All images now use 0x80 (Standard/Legacy) as required by AOL.
     * This works correctly now that the GCT/LCT structure is fixed.
     */
    @Test
    void shouldCalculateResourceTypeBasedOnTransparency() {
        // Transparent images should use Standard/Legacy flag (0x80)
        assertEquals(0x80, AolArtFlagCalculator.calculateResourceTypeFlag(true),
                "Transparent images should use Standard/Legacy flag (0x80) as required by AOL");

        // Opaque images should ALSO use Standard/Legacy flag (0x80)
        assertEquals(0x80, AolArtFlagCalculator.calculateResourceTypeFlag(false),
                "Opaque images should use Standard/Legacy flag (0x80) as required by AOL");
    }

    /**
     * Test that all icon sizes now use DEFAULT category for safety.
     * (Tests updated after determining 0xFD is safest for all sizes)
     */
    @Test
    void allSizesShouldUseDefaultCategory() {
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(16, 16),
                "16×16 icons should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(32, 32),
                "32×32 icons should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(130, 64),
                "130×64 banners should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(500, 500),
                "Very large images should use DEFAULT (0xFD) for safety");
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(1000, 10),
                "Unusual aspect ratios should use DEFAULT (0xFD) for safety");
    }

    /**
     * Test size category descriptions.
     */
    @Test
    void shouldProvideCorrectSizeCategoryDescriptions() {
        assertEquals("Tiny Icon (16×16, ~256px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0x2A));
        assertEquals("Small Button (26×21, ~546px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0x1E));
        assertEquals("Square Icon (32×32, ~1024px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0x55));
        assertEquals("Standard Icon (19×16, ~304px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0xE3));
        assertEquals("Large Graphic (~4858px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0x00));
        assertEquals("XL Banner (130×64, ~8320px)",
                AolArtFlagCalculator.getSizeCategoryDescription(0x0F));
        assertEquals("DEFAULT (any size)",
                AolArtFlagCalculator.getSizeCategoryDescription(0xFD));
    }

    /**
     * Test resource type descriptions.
     */
    @Test
    void shouldProvideCorrectResourceTypeDescriptions() {
        assertEquals("Standard/Legacy",
                AolArtFlagCalculator.getResourceTypeDescription(0x80));
        assertEquals("Modern/Advanced",
                AolArtFlagCalculator.getResourceTypeDescription(0x00));
    }

    /**
     * Test that custom/unknown flag values get appropriate descriptions.
     */
    @Test
    void shouldHandleCustomFlagValues() {
        assertTrue(AolArtFlagCalculator.getSizeCategoryDescription(0x99).contains("Custom"));
        assertTrue(AolArtFlagCalculator.getResourceTypeDescription(0x42).contains("Custom"));
    }

    /**
     * Integration test: Validate common use cases with DEFAULT size category.
     */
    @Test
    void shouldHandleCommonUseCases() {
        // Small toolbar icon with transparency
        int sizeFlag1 = AolArtFlagCalculator.calculateSizeCategoryFlag(16, 16);
        int typeFlag1 = AolArtFlagCalculator.calculateResourceTypeFlag(true);
        assertEquals(0xFD, sizeFlag1, "16×16 should use DEFAULT (0xFD)");
        assertEquals(0x80, typeFlag1, "Transparent should use Standard/Legacy (0x80) as required by AOL");

        // Standard button without transparency
        int sizeFlag2 = AolArtFlagCalculator.calculateSizeCategoryFlag(26, 21);
        int typeFlag2 = AolArtFlagCalculator.calculateResourceTypeFlag(false);
        assertEquals(0xFD, sizeFlag2, "26×21 should use DEFAULT (0xFD)");
        assertEquals(0x80, typeFlag2, "Opaque should use Standard/Legacy (0x80) as required by AOL");

        // Large graphic with transparency
        int sizeFlag3 = AolArtFlagCalculator.calculateSizeCategoryFlag(80, 60);
        int typeFlag3 = AolArtFlagCalculator.calculateResourceTypeFlag(true);
        assertEquals(0xFD, sizeFlag3, "80×60 should use DEFAULT (0xFD)");
        assertEquals(0x80, typeFlag3, "Transparent should use Standard/Legacy (0x80) as required by AOL");
    }

    /**
     * Test edge case: 1×1 pixel image.
     */
    @Test
    void shouldHandleSinglePixelImage() {
        int result = AolArtFlagCalculator.calculateSizeCategoryFlag(1, 1);
        assertEquals(0xFD, result, "1×1 pixel should use DEFAULT (0xFD)");
    }

    /**
     * Test edge case: very wide banner.
     */
    @Test
    void shouldHandleWideBanner() {
        int result = AolArtFlagCalculator.calculateSizeCategoryFlag(468, 60);
        assertEquals(0xFD, result, "Very wide banner should use DEFAULT (0xFD)");
    }

    /**
     * Validate that all sizes now use DEFAULT for maximum compatibility.
     * (Previously tested size-specific thresholds, but those can cause issues)
     */
    @Test
    void shouldAlwaysUseDefaultForMaximumCompatibility() {
        // All sizes now use 0xFD (DEFAULT) regardless of dimensions
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(17, 17)); // 289px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(20, 20)); // 400px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(26, 21)); // 546px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(32, 32)); // 1024px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(70, 70)); // 4900px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(90, 90)); // 8100px
        assertEquals(0xFD, AolArtFlagCalculator.calculateSizeCategoryFlag(100, 100)); // 10000px
    }
}
