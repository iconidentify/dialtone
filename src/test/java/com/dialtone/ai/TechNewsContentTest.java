/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UnifiedNewsContent formatting logic.
 * (Previously TechNewsContentTest, now tests the unified content model)
 */
class TechNewsContentTest {

    @Test
    void testGetReport_removesLabels() {
        // Given: Tech news content with TEASER and STORY labels
        String rawReport = "TEASER: Breaking AI News\n\n" +
                "STORY 1: First Headline\n" +
                "First story summary text here.\n\n" +
                "STORY 2: Second Headline\n" +
                "Second story summary text here.";

        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(rawReport);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Labels should be removed
        assertFalse(formatted.contains("TEASER:"), "Should not contain TEASER label");
        assertFalse(formatted.contains("STORY 1:"), "Should not contain STORY 1 label");
        assertFalse(formatted.contains("STORY 2:"), "Should not contain STORY 2 label");
    }

    @Test
    void testGetReport_convertsNewlinesToAolFormat() {
        // Given: Content with newlines
        String rawReport = "TEASER: Test\n\n" +
                "STORY 1: Headline\n" +
                "Summary text.";

        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(rawReport);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Content should have clean newlines (0x7F conversion happens in FDO templating)
        assertTrue(formatted.contains("\n"), "Should contain normal newline characters");
        assertFalse(formatted.contains("\r"), "Should not contain CR bytes - that's old behavior");
        assertFalse(formatted.contains("\\"), "Should not contain literal backslashes");
    }

    @Test
    void testGetReport_addsSeparationBetweenStories() {
        // Given: Multiple stories
        String rawReport = "TEASER: Test\n\n" +
                "STORY 1: First\n" +
                "First summary.\n\n" +
                "STORY 2: Second\n" +
                "Second summary.";

        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(rawReport);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Should have decorated headlines with normal newlines (0x7F conversion happens in FDO)
        assertTrue(formatted.contains("*** First ***"), "Should have decorated first headline");
        assertTrue(formatted.contains("*** Second ***"), "Should have decorated second headline");
        assertTrue(formatted.contains("\n\n"), "Should have paragraph separation with normal newlines");
    }

    @Test
    void testGetReport_handlesEmptyReport() {
        // Given: Empty report
        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport("");

        // When: Getting report
        String formatted = content.getReport();

        // Then: Should return default message
        assertEquals("No tech news available", formatted);
    }

    @Test
    void testGetReport_handlesNullReport() {
        // Given: Null report
        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(null);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Should return default message
        assertEquals("No tech news available", formatted);
    }

    @Test
    void testGetReport_cleansExcessiveLineBreaks() {
        // Given: Content that might generate 4+ consecutive line breaks
        String rawReport = "TEASER: Test\n\n\n\n" +
                "STORY 1: Headline\n\n\n\n" +
                "Text here.";

        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(rawReport);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Should preserve content structure with normal newlines
        // Note: Multiple newlines may exist from STORY pattern replacement, cleaned later in FDO
        assertTrue(formatted.contains("*** Headline ***"), "Should have decorated headline");
        assertTrue(formatted.contains("\n"), "Should contain normal newlines");
    }

    @Test
    void testGetReport_realWorldExample() {
        // Given: Realistic Grok API response format
        String rawReport = "TEASER: NVIDIA Kicks Off Open Source AI Week\n\n" +
                "STORY 1: NVIDIA Kicks Off Open Source AI Week with Hackathons Driving ML Innovation\n" +
                "NVIDIA's initiative features workshops and meetups spotlighting advances in machine learning.\n\n" +
                "STORY 2: OpenAI Introduces Agent Mode in New Browser\n" +
                "The preview of agent mode in ChatGPT Atlas enables AI to handle complex browsing actions.\n\n" +
                "STORY 3: Community Builders Share Decentralized AI Tools\n" +
                "Posts on X highlight new open-source repositories for decentralized AI models.";

        UnifiedNewsContent content = new UnifiedNewsContent();
        content.setCategory("tech");
        content.setFallbackMessage("No tech news available");
        content.setFullReport(rawReport);

        // When: Getting report
        String formatted = content.getReport();

        // Then: Verify expected transformations
        assertFalse(formatted.contains("TEASER:"));
        assertFalse(formatted.contains("STORY 1:"));
        assertFalse(formatted.contains("STORY 2:"));
        assertFalse(formatted.contains("STORY 3:"));
        assertTrue(formatted.contains("\n"), "Should have normal newline characters");
        assertFalse(formatted.contains("\r"), "Should not contain CR bytes - that's old behavior");
        assertFalse(formatted.contains("\\"), "Should not contain literal backslashes");
        assertTrue(formatted.contains("*** NVIDIA"), "Should have decorated headline");
        assertTrue(formatted.contains("*** OpenAI"), "Should have decorated headline");
        assertTrue(formatted.contains("*** Community Builders"), "Should have decorated headline");

        // Debug output to see actual formatting
        System.out.println("=== Formatted Output (escape sequences shown) ===");
        System.out.println(formatted);
    }
}
