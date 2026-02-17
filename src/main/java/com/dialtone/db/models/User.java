/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.db.models;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;

/**
 * Represents a user account linked to OAuth authentication (X or Discord).
 *
 * Users are created when someone signs in via OAuth for the first time.
 * Each user can have up to 3 AOL screennames.
 */
public record User(
    @SerializedName("id")
    Integer id,

    @SerializedName("auth_provider")
    String authProvider,

    @SerializedName("x_user_id")
    String xUserId,

    @SerializedName("x_username")
    String xUsername,

    @SerializedName("x_display_name")
    String xDisplayName,

    @SerializedName("discord_user_id")
    String discordUserId,

    @SerializedName("discord_username")
    String discordUsername,

    @SerializedName("discord_display_name")
    String discordDisplayName,

    @SerializedName("email")
    String email,

    @SerializedName("created_at")
    LocalDateTime createdAt,

    @SerializedName("is_active")
    Boolean isActive
) {
    /**
     * Auth provider constants.
     */
    public static final String PROVIDER_X = "x";
    public static final String PROVIDER_DISCORD = "discord";
    public static final String PROVIDER_EMAIL = "email";

    /**
     * Creates a new User for X OAuth registration.
     * Used when creating a user for the first time via X.
     */
    public static User createNewXUser(String xUserId, String xUsername, String xDisplayName) {
        return new User(
            null, // ID will be assigned by database
            PROVIDER_X,
            xUserId,
            xUsername,
            xDisplayName,
            null, // No Discord ID
            null, // No Discord username
            null, // No Discord display name
            null, // No email
            LocalDateTime.now(),
            true
        );
    }

    /**
     * Creates a new User for Discord OAuth registration.
     * Used when creating a user for the first time via Discord.
     */
    public static User createNewDiscordUser(String discordUserId, String discordUsername, String discordDisplayName) {
        return new User(
            null, // ID will be assigned by database
            PROVIDER_DISCORD,
            null, // No X user ID
            null, // No X username
            null, // No X display name
            discordUserId,
            discordUsername,
            discordDisplayName,
            null, // No email
            LocalDateTime.now(),
            true
        );
    }

    /**
     * Creates a new User for email authentication.
     * Used when creating a user for the first time via magic link.
     */
    public static User createNewEmailUser(String email) {
        return new User(
            null, // ID will be assigned by database
            PROVIDER_EMAIL,
            null, // No X user ID
            null, // No X username
            null, // No X display name
            null, // No Discord ID
            null, // No Discord username
            null, // No Discord display name
            email,
            LocalDateTime.now(),
            true
        );
    }

    /**
     * Legacy factory method for backward compatibility.
     * Creates a new X user.
     */
    public static User createNew(String xUserId, String xUsername, String xDisplayName) {
        return createNewXUser(xUserId, xUsername, xDisplayName);
    }

    /**
     * Creates a User from database row data with all fields.
     * Used when loading users from the database.
     */
    public static User fromDatabase(int id, String authProvider, String xUserId, String xUsername,
                                  String xDisplayName, String discordUserId, String discordUsername,
                                  String discordDisplayName, String email, LocalDateTime createdAt, boolean isActive) {
        return new User(id, authProvider, xUserId, xUsername, xDisplayName,
                       discordUserId, discordUsername, discordDisplayName, email, createdAt, isActive);
    }

    /**
     * Legacy factory method for backward compatibility (without email).
     * Creates a User from database row data.
     */
    public static User fromDatabase(int id, String authProvider, String xUserId, String xUsername,
                                  String xDisplayName, String discordUserId, String discordUsername,
                                  String discordDisplayName, LocalDateTime createdAt, boolean isActive) {
        return new User(id, authProvider, xUserId, xUsername, xDisplayName,
                       discordUserId, discordUsername, discordDisplayName, null, createdAt, isActive);
    }

    /**
     * Legacy factory method for backward compatibility.
     * Creates a User from database row data (X-only).
     */
    public static User fromDatabase(int id, String xUserId, String xUsername,
                                  String xDisplayName, LocalDateTime createdAt, boolean isActive) {
        return new User(id, PROVIDER_X, xUserId, xUsername, xDisplayName,
                       null, null, null, null, createdAt, isActive);
    }

    /**
     * Gets the display name for UI purposes.
     * Falls back to provider username if display name is null.
     */
    public String getDisplayName() {
        if (PROVIDER_EMAIL.equals(authProvider)) {
            // For email users, use the email address (truncated if needed)
            if (email != null && !email.trim().isEmpty()) {
                return email.length() > 20 ? email.substring(0, 17) + "..." : email;
            }
            return "Email User";
        }
        if (PROVIDER_DISCORD.equals(authProvider)) {
            if (discordDisplayName != null && !discordDisplayName.trim().isEmpty()) {
                return discordDisplayName;
            }
            return discordUsername != null ? discordUsername : "Unknown";
        }
        // Default to X behavior
        if (xDisplayName != null && !xDisplayName.trim().isEmpty()) {
            return xDisplayName;
        }
        return xUsername != null ? "@" + xUsername : "Unknown";
    }

    /**
     * Gets the provider-specific username.
     */
    public String getProviderUsername() {
        if (PROVIDER_EMAIL.equals(authProvider)) {
            return email;
        }
        if (PROVIDER_DISCORD.equals(authProvider)) {
            return discordUsername;
        }
        return xUsername;
    }

    /**
     * Gets the provider-specific user ID.
     */
    public String getProviderUserId() {
        if (PROVIDER_EMAIL.equals(authProvider)) {
            return email; // Email is the unique identifier for email users
        }
        if (PROVIDER_DISCORD.equals(authProvider)) {
            return discordUserId;
        }
        return xUserId;
    }

    /**
     * Checks if this user authenticated via X.
     */
    public boolean isXUser() {
        return PROVIDER_X.equals(authProvider);
    }

    /**
     * Checks if this user authenticated via Discord.
     */
    public boolean isDiscordUser() {
        return PROVIDER_DISCORD.equals(authProvider);
    }

    /**
     * Checks if this user authenticated via email.
     */
    public boolean isEmailUser() {
        return PROVIDER_EMAIL.equals(authProvider);
    }

    /**
     * Checks if the user account is active.
     */
    public boolean isActiveUser() {
        return isActive != null && isActive;
    }

    /**
     * Creates a copy with updated X profile information.
     * Used when user's X profile changes.
     */
    public User withUpdatedXProfile(String newXUsername, String newXDisplayName) {
        return new User(
            id,
            authProvider,
            xUserId,
            newXUsername,
            newXDisplayName,
            discordUserId,
            discordUsername,
            discordDisplayName,
            email,
            createdAt,
            isActive
        );
    }

    /**
     * Creates a copy with updated Discord profile information.
     * Used when user's Discord profile changes.
     */
    public User withUpdatedDiscordProfile(String newDiscordUsername, String newDiscordDisplayName) {
        return new User(
            id,
            authProvider,
            xUserId,
            xUsername,
            xDisplayName,
            discordUserId,
            newDiscordUsername,
            newDiscordDisplayName,
            email,
            createdAt,
            isActive
        );
    }

    /**
     * Creates a copy with updated email.
     * Used when linking email to an existing account.
     */
    public User withUpdatedEmail(String newEmail) {
        return new User(
            id,
            authProvider,
            xUserId,
            xUsername,
            xDisplayName,
            discordUserId,
            discordUsername,
            discordDisplayName,
            newEmail,
            createdAt,
            isActive
        );
    }

    /**
     * Legacy method for backward compatibility.
     */
    public User withUpdatedProfile(String newUsername, String newDisplayName) {
        if (PROVIDER_DISCORD.equals(authProvider)) {
            return withUpdatedDiscordProfile(newUsername, newDisplayName);
        }
        return withUpdatedXProfile(newUsername, newDisplayName);
    }

    /**
     * Creates a copy with active status changed.
     */
    public User withActiveStatus(boolean active) {
        return new User(
            id,
            authProvider,
            xUserId,
            xUsername,
            xDisplayName,
            discordUserId,
            discordUsername,
            discordDisplayName,
            email,
            createdAt,
            active
        );
    }
}
