/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.User;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Base class for OAuth 2.0 authentication services.
 *
 * Provides shared PKCE code generation, state management, and database
 * operations for OAuth providers (X, Discord).
 */
public abstract class OAuthBaseService {
    protected static final long STATE_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    protected final DatabaseManager databaseManager;
    protected final Gson gson;
    protected final String redirectUri;

    // Store OAuth states to prevent CSRF attacks
    protected final ConcurrentHashMap<String, Long> pendingStates = new ConcurrentHashMap<>();
    // Store PKCE code verifiers for OAuth 2.0 security
    protected final ConcurrentHashMap<String, String> pendingCodeVerifiers = new ConcurrentHashMap<>();

    protected OAuthBaseService(DatabaseManager databaseManager, String redirectUri) {
        this.databaseManager = databaseManager;
        this.gson = new Gson();
        this.redirectUri = redirectUri;
    }

    // ========================================================================
    // PKCE Methods (RFC 7636)
    // ========================================================================

    /**
     * Generates secure random state for OAuth flow.
     */
    protected String generateSecureState() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Generates PKCE code verifier (RFC 7636).
     * Creates a cryptographically random string of 43-128 characters.
     */
    protected String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32]; // 32 bytes = 43 chars when base64url encoded
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    /**
     * Generates PKCE code challenge from code verifier (RFC 7636).
     * Creates SHA256 hash of the code verifier and base64url encodes it.
     */
    protected String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // ========================================================================
    // State Management
    // ========================================================================

    /**
     * Stores state and code verifier for OAuth flow.
     */
    protected void storeStateAndVerifier(String state, String codeVerifier) {
        pendingStates.put(state, System.currentTimeMillis());
        pendingCodeVerifiers.put(state, codeVerifier);
        cleanupExpiredStates();
    }

    /**
     * Gets the code verifier for a given state.
     */
    protected String getCodeVerifier(String state) {
        return pendingCodeVerifiers.get(state);
    }

    /**
     * Cleans up state and code verifier after successful authentication.
     */
    protected void cleanupStateAndVerifier(String state) {
        pendingStates.remove(state);
        pendingCodeVerifiers.remove(state);
    }

    /**
     * Validates state parameter for CSRF protection.
     */
    protected boolean validateState(String state) {
        Long timestamp = pendingStates.get(state);
        if (timestamp == null) {
            return false;
        }
        return (System.currentTimeMillis() - timestamp) < STATE_TIMEOUT_MS;
    }

    /**
     * Cleans up expired state tokens and code verifiers.
     */
    protected void cleanupExpiredStates() {
        long now = System.currentTimeMillis();

        // Get all expired states
        Set<String> expiredStates = pendingStates.entrySet().stream()
            .filter(entry -> (now - entry.getValue()) > STATE_TIMEOUT_MS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // Remove expired states and their corresponding code verifiers
        expiredStates.forEach(state -> {
            pendingStates.remove(state);
            pendingCodeVerifiers.remove(state);
        });
    }

    // ========================================================================
    // Database Operations
    // ========================================================================

    /**
     * Creates new user in database.
     */
    protected User createUser(User user) throws SQLException {
        String sql = "INSERT INTO users (auth_provider, x_user_id, x_username, x_display_name, " +
                    "discord_user_id, discord_username, discord_display_name, created_at, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.authProvider());
            stmt.setString(2, user.xUserId());
            stmt.setString(3, user.xUsername());
            stmt.setString(4, user.xDisplayName());
            stmt.setString(5, user.discordUserId());
            stmt.setString(6, user.discordUsername());
            stmt.setString(7, user.discordDisplayName());
            stmt.setObject(8, user.createdAt());
            stmt.setBoolean(9, user.isActive());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                return User.fromDatabase(
                    id, user.authProvider(), user.xUserId(), user.xUsername(), user.xDisplayName(),
                    user.discordUserId(), user.discordUsername(), user.discordDisplayName(),
                    user.createdAt(), user.isActive()
                );
            }
        }
        throw new SQLException("Failed to create user - no ID returned");
    }

    /**
     * Parses User from database ResultSet.
     */
    protected User userFromResultSet(ResultSet rs) throws SQLException {
        String authProvider = rs.getString("auth_provider");
        if (authProvider == null) {
            // Fallback for legacy records - determine provider from which fields are set
            if (rs.getString("x_user_id") != null) {
                authProvider = User.PROVIDER_X;
            } else if (rs.getString("discord_user_id") != null) {
                authProvider = User.PROVIDER_DISCORD;
            } else {
                authProvider = User.PROVIDER_X; // Default fallback
            }
        }

        return User.fromDatabase(
            rs.getInt("id"),
            authProvider,
            rs.getString("x_user_id"),
            rs.getString("x_username"),
            rs.getString("x_display_name"),
            rs.getString("discord_user_id"),
            rs.getString("discord_username"),
            rs.getString("discord_display_name"),
            LocalDateTime.parse(rs.getString("created_at").replace(" ", "T")),
            rs.getBoolean("is_active")
        );
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Helper method for null-safe string comparison.
     */
    protected boolean nullSafeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    // ========================================================================
    // Shared Exception
    // ========================================================================

    /**
     * Exception thrown when authentication fails.
     */
    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
