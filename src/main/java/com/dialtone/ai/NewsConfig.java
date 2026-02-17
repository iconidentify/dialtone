/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

/**
 * Configuration for a single news category.
 * Holds all category-specific settings loaded from application.properties.
 */
public class NewsConfig {

    private final String categoryName;
    private final String prompt;
    private final int refreshIntervalMinutes;
    private final int dailyQuota;
    private final int storySentences;
    private final int headlineWordsMin;
    private final int headlineWordsMax;
    private final String fallbackMessage;
    private final String persistDirectory;
    private final int keepFileCount;

    private NewsConfig(Builder builder) {
        this.categoryName = builder.categoryName;
        this.prompt = builder.prompt;
        this.refreshIntervalMinutes = builder.refreshIntervalMinutes;
        this.dailyQuota = builder.dailyQuota;
        this.storySentences = builder.storySentences;
        this.headlineWordsMin = builder.headlineWordsMin;
        this.headlineWordsMax = builder.headlineWordsMax;
        this.fallbackMessage = builder.fallbackMessage;
        this.persistDirectory = builder.persistDirectory;
        this.keepFileCount = builder.keepFileCount;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public int getStorySentences() {
        return storySentences;
    }

    public int getHeadlineWordsMin() {
        return headlineWordsMin;
    }

    public int getHeadlineWordsMax() {
        return headlineWordsMax;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public String getPersistDirectory() {
        return persistDirectory;
    }

    public int getKeepFileCount() {
        return keepFileCount;
    }

    @Override
    public String toString() {
        return "NewsConfig{" +
                "category='" + categoryName + '\'' +
                ", refreshInterval=" + refreshIntervalMinutes + "min" +
                ", quota=" + dailyQuota + "/day" +
                ", persistDir='" + persistDirectory + '\'' +
                '}';
    }

    /**
     * Builder for NewsConfig.
     */
    public static class Builder {
        private String categoryName;
        private String prompt;
        private int refreshIntervalMinutes = 1440; // 24 hours default
        private int dailyQuota = 3;
        private int storySentences = 4;
        private int headlineWordsMin = 12;
        private int headlineWordsMax = 18;
        private String fallbackMessage = "No news available";
        private String persistDirectory;
        private int keepFileCount = 10;

        public Builder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder refreshIntervalMinutes(int refreshIntervalMinutes) {
            this.refreshIntervalMinutes = refreshIntervalMinutes;
            return this;
        }

        public Builder dailyQuota(int dailyQuota) {
            this.dailyQuota = dailyQuota;
            return this;
        }

        public Builder storySentences(int storySentences) {
            this.storySentences = storySentences;
            return this;
        }

        public Builder headlineWordsMin(int headlineWordsMin) {
            this.headlineWordsMin = headlineWordsMin;
            return this;
        }

        public Builder headlineWordsMax(int headlineWordsMax) {
            this.headlineWordsMax = headlineWordsMax;
            return this;
        }

        public Builder fallbackMessage(String fallbackMessage) {
            this.fallbackMessage = fallbackMessage;
            return this;
        }

        public Builder persistDirectory(String persistDirectory) {
            this.persistDirectory = persistDirectory;
            return this;
        }

        public Builder keepFileCount(int keepFileCount) {
            this.keepFileCount = keepFileCount;
            return this;
        }

        public NewsConfig build() {
            if (categoryName == null || categoryName.isEmpty()) {
                throw new IllegalArgumentException("Category name is required");
            }
            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException("Prompt is required");
            }
            if (persistDirectory == null || persistDirectory.isEmpty()) {
                throw new IllegalArgumentException("Persist directory is required");
            }
            return new NewsConfig(this);
        }
    }
}
