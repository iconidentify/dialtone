/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.db.models;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

/**
 * Represents an AOL screenname linked to a user account.
 *
 * Each user can have up to 3 screennames. Screennames must be unique across all users,
 * are limited to 10 characters, and have passwords limited to 8 characters for AOL protocol compatibility.
 */
public record Screenname(
    @SerializedName("id")
    Integer id,

    @SerializedName("user_id")
    Integer userId,

    @SerializedName("screenname")
    String screenname,

    @SerializedName("password_hash")
    String passwordHash,

    @SerializedName("is_primary")
    Boolean isPrimary,

    @SerializedName("created_at")
    LocalDateTime createdAt
) {
    /**
     * Creates a new Screenname for a user.
     * Used when creating a screenname for the first time.
     */
    public static Screenname createNew(int userId, String screenname, String passwordHash, boolean isPrimary) {
        return new Screenname(
            null, // ID will be assigned by database
            userId,
            screenname,
            passwordHash,
            isPrimary,
            LocalDateTime.now()
        );
    }

    /**
     * Creates a Screenname from database row data.
     * Used when loading screennames from the database.
     */
    public static Screenname fromDatabase(int id, int userId, String screenname,
                                        String passwordHash, boolean isPrimary, LocalDateTime createdAt) {
        return new Screenname(id, userId, screenname, passwordHash, isPrimary, createdAt);
    }

    /**
     * Reserved prefix for ephemeral guest accounts.
     * Normal registered accounts cannot use this prefix.
     */
    public static final String RESERVED_PREFIX = "~";

    /**
     * Validates screenname format.
     * Must be 1-10 characters, alphanumeric only.
     * Cannot start with reserved prefix (~) which is for ephemeral guests only.
     */
    public static boolean isValidScreenname(String screenname) {
        if (screenname == null || screenname.trim().isEmpty()) {
            return false;
        }

        String trimmed = screenname.trim();

        // Check for reserved prefix (ephemeral guests only)
        if (startsWithReservedPrefix(trimmed)) {
            return false;
        }

        if (trimmed.length() < 1 || trimmed.length() > 10) {
            return false;
        }

        // Check for alphanumeric characters only
        return trimmed.matches("^[a-zA-Z0-9]+$");
    }

    /**
     * Checks if a screenname starts with the reserved prefix (~).
     * The ~ prefix is reserved for ephemeral guest accounts only.
     *
     * @param screenname The screenname to check
     * @return true if starts with reserved prefix
     */
    public static boolean startsWithReservedPrefix(String screenname) {
        return screenname != null && screenname.startsWith(RESERVED_PREFIX);
    }

    /**
     * Validates password format for AOL protocol compatibility.
     * Must be 1-8 characters for AOL protocol requirements.
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        return password.length() >= 1 && password.length() <= 8;
    }

    /**
     * Checks if this screenname is marked as primary.
     */
    public boolean isPrimaryScreenname() {
        return isPrimary != null && isPrimary;
    }

    /**
     * Creates a copy with updated screenname.
     * Used when user changes their screenname.
     */
    public Screenname withScreenname(String newScreenname) {
        return new Screenname(
            id,
            userId,
            newScreenname,
            passwordHash,
            isPrimary,
            createdAt
        );
    }

    /**
     * Creates a copy with updated password hash.
     * Used when user changes their password.
     */
    public Screenname withPasswordHash(String newPasswordHash) {
        return new Screenname(
            id,
            userId,
            screenname,
            newPasswordHash,
            isPrimary,
            createdAt
        );
    }

    /**
     * Creates a copy with updated primary status.
     * Used when user sets/unsets this as their primary screenname.
     */
    public Screenname withPrimary(boolean primary) {
        return new Screenname(
            id,
            userId,
            screenname,
            passwordHash,
            primary,
            createdAt
        );
    }

    /**
     * Creates a sanitized version for JSON responses.
     * Excludes the password hash for security.
     */
    public ScreennameResponse toResponse() {
        return new ScreennameResponse(id, screenname, isPrimary, createdAt);
    }

    /**
     * Response DTO that excludes sensitive data (password hash).
     */
    public record ScreennameResponse(
        @SerializedName("id")
        Integer id,

        @SerializedName("screenname")
        String screenname,

        @SerializedName("is_primary")
        Boolean isPrimary,

        @SerializedName("created_at")
        LocalDateTime createdAt
    ) {}
}