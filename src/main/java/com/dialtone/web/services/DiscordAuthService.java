/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Service for handling Discord OAuth 2.0 authentication.
 *
 * Manages the OAuth flow, user profile retrieval, and integration
 * with the User database model for web interface authentication.
 */
public class DiscordAuthService extends OAuthBaseService {
    private final OAuth20Service oauthService;

    public DiscordAuthService(Properties config) {
        super(
            DatabaseManager.getInstance(config.getProperty("db.path", "db/dialtone.db")),
            config.getProperty("discord.oauth.redirect.uri", "http://localhost:5200/api/auth/discord/callback")
        );

        // Configure Discord OAuth 2.0 service
        String clientId = config.getProperty("discord.oauth.client.id");
        String clientSecret = config.getProperty("discord.oauth.client.secret");

        if (clientId == null || clientSecret == null) {
            LoggerUtil.warn("Discord OAuth client ID and/or secret not configured - Discord login will be disabled");
            this.oauthService = null;
            return;
        }

        this.oauthService = new ServiceBuilder(clientId)
            .apiSecret(clientSecret)
            .defaultScope("identify")
            .callback(redirectUri)
            .build(DiscordApi20.instance());

        LoggerUtil.info("Discord OAuth service initialized with redirect URI: " + redirectUri);
    }

    /**
     * Checks if Discord OAuth is configured and available.
     */
    public boolean isEnabled() {
        return oauthService != null;
    }

    /**
     * Generates authorization URL for Discord OAuth login.
     * Creates a unique state parameter to prevent CSRF attacks.
     *
     * @return OAuth authorization URL with state parameter
     * @throws IllegalStateException if Discord OAuth is not configured
     */
    public String getAuthorizationUrl() {
        if (!isEnabled()) {
            throw new IllegalStateException("Discord OAuth is not configured");
        }

        String state = generateSecureState();
        String codeVerifier = generateCodeVerifier();

        storeStateAndVerifier(state, codeVerifier);

        String codeChallenge = generateCodeChallenge(codeVerifier);
        String authUrl = oauthService.createAuthorizationUrlBuilder()
            .state(state)
            .additionalParams(Map.of(
                "code_challenge", codeChallenge,
                "code_challenge_method", "S256"
            ))
            .build();

        LoggerUtil.debug("Generated Discord OAuth authorization URL with state: " + state + " and PKCE challenge");
        return authUrl;
    }

    /**
     * Handles OAuth callback and exchanges authorization code for access token.
     * Retrieves user profile and creates/updates User record.
     *
     * @param code Authorization code from Discord
     * @param state State parameter for CSRF protection
     * @return User object if authentication succeeds
     * @throws AuthenticationException if authentication fails
     */
    public User handleCallback(String code, String state) throws AuthenticationException {
        if (!isEnabled()) {
            throw new AuthenticationException("Discord OAuth is not configured");
        }

        try {
            // Validate state parameter
            if (!validateState(state)) {
                throw new AuthenticationException("Invalid or expired state parameter");
            }

            // Get the code verifier for this state
            String codeVerifier = getCodeVerifier(state);
            if (codeVerifier == null) {
                throw new AuthenticationException("PKCE code verifier not found for state: " + state);
            }

            // Exchange authorization code for access token using PKCE
            AccessTokenRequestParams tokenRequest = AccessTokenRequestParams
                .create(code)
                .addExtraParameter("code_verifier", codeVerifier);
            OAuth2AccessToken accessToken = oauthService.getAccessToken(tokenRequest);
            LoggerUtil.debug("Successfully obtained Discord OAuth access token");

            // Get user profile from Discord API
            DiscordUserProfile userProfile = getUserProfile(accessToken);
            LoggerUtil.info("Retrieved Discord user profile: " + userProfile.username);

            // Create or update User in database
            User user = findOrCreateUser(userProfile);

            // Clean up the used state and code verifier
            cleanupStateAndVerifier(state);

            return user;

        } catch (Exception e) {
            LoggerUtil.error("Discord OAuth callback failed: " + e.getMessage());
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves user profile information from Discord API using access token.
     */
    private DiscordUserProfile getUserProfile(OAuth2AccessToken accessToken) throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, DiscordApi20.USER_INFO_ENDPOINT);
        oauthService.signRequest(accessToken, request);

        Response response = oauthService.execute(request);

        if (!response.isSuccessful()) {
            throw new Exception("Failed to get Discord user profile: " + response.getBody());
        }

        JsonObject userData = gson.fromJson(response.getBody(), JsonObject.class);

        String id = userData.get("id").getAsString();
        String username = userData.get("username").getAsString();

        // Discord's global_name is the display name, fallback to username
        String displayName = userData.has("global_name") && !userData.get("global_name").isJsonNull()
            ? userData.get("global_name").getAsString()
            : username;

        return new DiscordUserProfile(id, username, displayName);
    }

    /**
     * Finds existing user or creates new one based on Discord profile.
     */
    private User findOrCreateUser(DiscordUserProfile profile) throws SQLException {
        // First try to find existing user by Discord user ID
        User existingUser = findUserByDiscordUserId(profile.id);

        if (existingUser != null) {
            // Update profile information if it changed
            if (!profile.username.equals(existingUser.discordUsername()) ||
                !nullSafeEquals(profile.displayName, existingUser.discordDisplayName())) {

                User updatedUser = existingUser.withUpdatedDiscordProfile(profile.username, profile.displayName);
                updateUser(updatedUser);
                LoggerUtil.info("Updated Discord profile for user: " + existingUser.id());
                return updatedUser;
            }
            return existingUser;
        }

        // Create new user
        User newUser = User.createNewDiscordUser(profile.id, profile.username, profile.displayName);
        User savedUser = createUser(newUser);
        LoggerUtil.info("Created new user account for Discord user: " + profile.username);
        return savedUser;
    }

    /**
     * Finds user by Discord user ID.
     */
    private User findUserByDiscordUserId(String discordUserId) throws SQLException {
        String sql = "SELECT id, auth_provider, x_user_id, x_username, x_display_name, " +
                    "discord_user_id, discord_username, discord_display_name, created_at, is_active " +
                    "FROM users WHERE discord_user_id = ? AND is_active = 1";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordUserId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return userFromResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Updates existing user in database.
     */
    private void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET discord_username = ?, discord_display_name = ? WHERE id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.discordUsername());
            stmt.setString(2, user.discordDisplayName());
            stmt.setInt(3, user.id());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("Failed to update user - no rows affected");
            }
        }
    }

    /**
     * Discord user profile data from API.
     */
    public static record DiscordUserProfile(String id, String username, String displayName) {}
}
