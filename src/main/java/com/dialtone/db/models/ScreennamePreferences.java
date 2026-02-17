/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.db.models;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

/**
 * Preferences for an individual screenname.
 * One-to-one relationship with the screennames table.
 *
 * Supports lazy creation - defaults are returned if no row exists in the database.
 * This allows the protocol handlers to function without requiring all screennames
 * to have an explicit preferences row.
 */
public record ScreennamePreferences(
    @SerializedName("id")
    Integer id,

    @SerializedName("screenname_id")
    Integer screennameId,

    @SerializedName("low_color_mode")
    Boolean lowColorMode,

    @SerializedName("created_at")
    LocalDateTime createdAt,

    @SerializedName("updated_at")
    LocalDateTime updatedAt
) {
    /**
     * Default preferences for screennames without an explicit preferences row.
     * Used when no row exists in the database.
     *
     * @param screennameId The screenname ID to associate defaults with
     * @return Default preferences with low_color_mode = false
     */
    public static ScreennamePreferences defaults(int screennameId) {
        return new ScreennamePreferences(
            null,
            screennameId,
            false,  // Default to standard orange theme
            null,
            null
        );
    }

    /**
     * Creates a ScreennamePreferences from database row data.
     *
     * @param id Database row ID
     * @param screennameId Associated screenname ID
     * @param lowColorMode Whether low color mode is enabled
     * @param createdAt Row creation timestamp
     * @param updatedAt Last update timestamp
     * @return ScreennamePreferences instance
     */
    public static ScreennamePreferences fromDatabase(int id, int screennameId, boolean lowColorMode,
                                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ScreennamePreferences(id, screennameId, lowColorMode, createdAt, updatedAt);
    }

    /**
     * Creates preferences for a new screenname (first save).
     *
     * @param screennameId The screenname ID
     * @param lowColorMode Whether to enable low color mode
     * @return New preferences ready for database insertion
     */
    public static ScreennamePreferences createNew(int screennameId, boolean lowColorMode) {
        LocalDateTime now = LocalDateTime.now();
        return new ScreennamePreferences(
            null,  // ID assigned by database
            screennameId,
            lowColorMode,
            now,
            now
        );
    }

    /**
     * Check if low color mode is enabled.
     * Handles null safely, defaulting to false.
     *
     * @return true if low color mode is enabled
     */
    public boolean isLowColorModeEnabled() {
        return lowColorMode != null && lowColorMode;
    }

    /**
     * Creates a copy with updated low color mode setting.
     *
     * @param enabled Whether low color mode should be enabled
     * @return New preferences with updated low_color_mode
     */
    public ScreennamePreferences withLowColorMode(boolean enabled) {
        return new ScreennamePreferences(
            id,
            screennameId,
            enabled,
            createdAt,
            LocalDateTime.now()  // Update the timestamp
        );
    }

    /**
     * Converts to a response DTO suitable for API responses.
     *
     * @return Response object with only public fields
     */
    public PreferencesResponse toResponse() {
        return new PreferencesResponse(screennameId, isLowColorModeEnabled());
    }

    /**
     * Response DTO for API responses.
     * Contains only the fields that should be exposed to clients.
     */
    public record PreferencesResponse(
        @SerializedName("screenname_id")
        Integer screennameId,

        @SerializedName("low_color_mode")
        Boolean lowColorMode
    ) {}
}
