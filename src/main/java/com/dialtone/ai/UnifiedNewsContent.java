/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified domain model for all news content types.
 * Replaces separate NewsContent, CryptoNewsContent, SportsContent, EntertainmentContent, TechNewsContent.
 * Uses category field to distinguish between different news types.
 * Serializable to/from JSON for persistence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedNewsContent extends AbstractNewsContent {

    @JsonProperty("category")
    private String category;

    @JsonProperty("teaserHeadline")
    private String teaserHeadline;

    @JsonProperty("stories")
    private List<Story> stories;

    @JsonProperty("fallbackMessage")
    private String fallbackMessage;

    public UnifiedNewsContent() {
        super();
        this.stories = new ArrayList<>();
        this.fallbackMessage = "No news available";
    }

    public UnifiedNewsContent(String category, String teaserHeadline, List<Story> stories, String fullReport) {
        super();
        this.category = category;
        this.teaserHeadline = teaserHeadline;
        this.stories = stories;
        this.fullReport = fullReport;
        this.fallbackMessage = "No " + category + " news available";
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTeaserHeadline() {
        return teaserHeadline;
    }

    public void setTeaserHeadline(String teaserHeadline) {
        this.teaserHeadline = teaserHeadline;
    }

    /**
     * Get headline for backward compatibility with welcome screen (CryptoNewsContent compatibility).
     * Returns the teaser headline.
     *
     * @return Teaser headline
     */
    public String getHeadline() {
        return teaserHeadline;
    }

    /**
     * Set headline for backward compatibility.
     * Sets the teaser headline.
     *
     * @param headline Headline text
     */
    public void setHeadline(String headline) {
        this.teaserHeadline = headline;
    }

    public List<Story> getStories() {
        return stories;
    }

    public void setStories(List<Story> stories) {
        this.stories = stories;
    }

    public void setFallbackMessage(String fallbackMessage) {
        this.fallbackMessage = fallbackMessage;
    }

    @Override
    protected String getFallbackMessage() {
        return fallbackMessage != null ? fallbackMessage : "No news available";
    }

    /**
     * Generic story with headline and summary.
     * Works for all news categories (general, crypto, sports, entertainment, tech).
     */
    public static class Story {
        @JsonProperty("headline")
        private String headline;

        @JsonProperty("summary")
        private String summary;

        public Story() {}

        public Story(String headline, String summary) {
            this.headline = headline;
            this.summary = summary;
        }

        public String getHeadline() {
            return headline;
        }

        public void setHeadline(String headline) {
            this.headline = headline;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return "Story{headline='" + headline + "', summary='" + summary + "'}";
        }
    }

    @Override
    public String toString() {
        return "UnifiedNewsContent{" +
                "category='" + category + '\'' +
                ", teaserHeadline='" + teaserHeadline + '\'' +
                ", generatedAt='" + generatedAt + '\'' +
                ", stories=" + stories.size() +
                '}';
    }
}
