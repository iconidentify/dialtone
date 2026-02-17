/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Tracks API fetch metadata to enforce daily quotas.
 * Prevents excessive API calls by limiting fetches to N per day.
 */
public class FetchMetadata {

    private Instant lastFetchTimestamp;
    private int fetchCountToday;
    private LocalDate lastResetDate;
    private final int dailyQuotaLimit;

    /**
     * Create new metadata with default values.
     *
     * @param dailyQuotaLimit Maximum fetches allowed per day
     */
    public FetchMetadata(int dailyQuotaLimit) {
        this.dailyQuotaLimit = dailyQuotaLimit;
        this.lastFetchTimestamp = null;
        this.fetchCountToday = 0;
        this.lastResetDate = LocalDate.now(ZoneId.systemDefault());
    }

    /**
     * Create metadata from persisted values.
     */
    public FetchMetadata(int dailyQuotaLimit, Instant lastFetchTimestamp, int fetchCountToday, LocalDate lastResetDate) {
        this.dailyQuotaLimit = dailyQuotaLimit;
        this.lastFetchTimestamp = lastFetchTimestamp;
        this.fetchCountToday = fetchCountToday;
        this.lastResetDate = lastResetDate != null ? lastResetDate : LocalDate.now(ZoneId.systemDefault());
    }

    /**
     * Check if we can fetch based on daily quota.
     *
     * @return true if fetch count is below daily limit
     */
    public boolean canFetch() {
        return fetchCountToday < dailyQuotaLimit;
    }

    /**
     * Record a successful fetch.
     * Increments counter and updates timestamp.
     */
    public void recordFetch() {
        this.lastFetchTimestamp = Instant.now();
        this.fetchCountToday++;
    }

    /**
     * Reset daily counter if it's a new day.
     * Call this before checking canFetch().
     */
    public void resetIfNewDay() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!today.equals(lastResetDate)) {
            fetchCountToday = 0;
            lastResetDate = today;
        }
    }

    /**
     * Get remaining fetches allowed today.
     */
    public int getRemainingToday() {
        return Math.max(0, dailyQuotaLimit - fetchCountToday);
    }

    // Getters
    public Instant getLastFetchTimestamp() {
        return lastFetchTimestamp;
    }

    public int getFetchCountToday() {
        return fetchCountToday;
    }

    public LocalDate getLastResetDate() {
        return lastResetDate;
    }

    public int getDailyQuotaLimit() {
        return dailyQuotaLimit;
    }

    // Setters (for deserialization)
    public void setLastFetchTimestamp(Instant lastFetchTimestamp) {
        this.lastFetchTimestamp = lastFetchTimestamp;
    }

    public void setFetchCountToday(int fetchCountToday) {
        this.fetchCountToday = fetchCountToday;
    }

    public void setLastResetDate(LocalDate lastResetDate) {
        this.lastResetDate = lastResetDate;
    }

    @Override
    public String toString() {
        return "FetchMetadata{" +
                "fetchCountToday=" + fetchCountToday +
                "/" + dailyQuotaLimit +
                ", lastFetch=" + lastFetchTimestamp +
                ", lastResetDate=" + lastResetDate +
                '}';
    }
}
