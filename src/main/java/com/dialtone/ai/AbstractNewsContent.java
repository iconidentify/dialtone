/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Abstract base class for news content models.
 * Provides shared formatting logic for all news content types.
 */
public abstract class AbstractNewsContent {

    @JsonProperty("fullReport")
    protected String fullReport;

    @JsonProperty("generatedAt")
    protected String generatedAt;

    /**
     * Default constructor initializes generatedAt timestamp.
     */
    public AbstractNewsContent() {
        this.generatedAt = Instant.now().toString();
    }

    /**
     * Get the full raw report text.
     *
     * @return Full report or null if not set
     */
    public String getFullReport() {
        return fullReport;
    }

    /**
     * Set the full raw report text.
     *
     * @param fullReport Report content from Grok AI
     */
    public void setFullReport(String fullReport) {
        this.fullReport = fullReport;
    }

    /**
     * Get the generation timestamp.
     *
     * @return ISO-8601 formatted timestamp
     */
    public String getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Set the generation timestamp.
     *
     * @param generatedAt ISO-8601 formatted timestamp
     */
    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Get the generation timestamp as an Instant for age calculations.
     *
     * @return Instant of generation time, or null if timestamp is invalid
     */
    public Instant getGeneratedAtInstant() {
        if (generatedAt == null || generatedAt.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(generatedAt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get report content with clean newlines.
     * Content is processed by the unified 0x7F newline conversion system.
     * This ensures consistent newline handling across all content types (TOS, news, etc.).
     *
     * @return Report text with clean newlines, or fallback message if unavailable
     */
    public String getReport() {
        if (fullReport == null || fullReport.isEmpty()) {
            return getFallbackMessage();
        }

        // Start with the full report
        String rawContent = fullReport;

        // Remove "TEASER:" line
        rawContent = rawContent.replaceAll("TEASER:[^\\n]*\\n+", "");

        // Remove "HEADLINE:" line (for crypto content)
        rawContent = rawContent.replaceAll("HEADLINE:[^\\n]*\\n+", "");

        // Replace "STORY N: HEADLINE" with decorated headline and spacing
        // Captures headline (first line after STORY N:) and adds *** decoration
        rawContent = rawContent.replaceAll("STORY \\d+:\\s*(.+?)\\n", "\n*** $1 ***\n\n     ");

        // NOTE: No newline conversion here - let unified 0x7F conversion handle it
        // This ensures consistent behavior with TOS content

        // Add generation timestamp at the end if available
        if (generatedAt != null && !generatedAt.isEmpty()) {
            rawContent = rawContent + "\n\nGenerated: " + generatedAt;
        }

        return rawContent.trim();
    }


    /**
     * Get the fallback message when no content is available.
     * Subclasses implement this to provide category-specific messages.
     *
     * @return Fallback message (e.g., "No news available")
     */
    protected abstract String getFallbackMessage();
}
