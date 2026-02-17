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
 * Service for handling X (Twitter) OAuth 2.0 authentication.
 *
 * Manages the OAuth flow, user profile retrieval, and integration
 * with the User database model for web interface authentication.
 */
public class XAuthService extends OAuthBaseService {
    private final OAuth20Service oauthService;

    public XAuthService(Properties config) {
        super(
            DatabaseManager.getInstance(config.getProperty("db.path", "db/dialtone.db")),
            config.getProperty("x.oauth.redirect.uri", "http://localhost:5200/api/auth/x/callback")
        );

        // Configure X OAuth 2.0 service
        String clientId = config.getProperty("x.oauth.client.id");
        String clientSecret = config.getProperty("x.oauth.client.secret");

        if (clientId == null || clientSecret == null) {
            throw new IllegalArgumentException(
                "X OAuth client ID and secret must be configured in application.properties");
        }

        this.oauthService = new ServiceBuilder(clientId)
            .apiSecret(clientSecret)
            .defaultScope("tweet.read users.read offline.access")
            .callback(redirectUri)
            .build(XApi20.instance());

        LoggerUtil.info("X OAuth service initialized with redirect URI: " + redirectUri);
    }

    /**
     * Generates authorization URL for X OAuth login.
     * Creates a unique state parameter to prevent CSRF attacks.
     *
     * @return OAuth authorization URL with state parameter
     */
    public String getAuthorizationUrl() {
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

        LoggerUtil.debug("Generated X OAuth authorization URL with state: " + state + " and PKCE challenge");
        return authUrl;
    }

    /**
     * Handles OAuth callback and exchanges authorization code for access token.
     * Retrieves user profile and creates/updates User record.
     *
     * @param code Authorization code from X
     * @param state State parameter for CSRF protection
     * @return User object if authentication succeeds
     * @throws AuthenticationException if authentication fails
     */
    public User handleCallback(String code, String state) throws AuthenticationException {
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
            LoggerUtil.debug("Successfully obtained X OAuth access token");

            // Get user profile from X API
            XUserProfile userProfile = getUserProfile(accessToken);
            LoggerUtil.info("Retrieved X user profile: @" + userProfile.username);

            // Create or update User in database
            User user = findOrCreateUser(userProfile);

            // Clean up the used state and code verifier
            cleanupStateAndVerifier(state);

            return user;

        } catch (Exception e) {
            LoggerUtil.error("X OAuth callback failed: " + e.getMessage());
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves user profile information from X API using access token.
     */
    private XUserProfile getUserProfile(OAuth2AccessToken accessToken) throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, XApi20.USER_INFO_ENDPOINT);
        oauthService.signRequest(accessToken, request);

        Response response = oauthService.execute(request);

        if (!response.isSuccessful()) {
            throw new Exception("Failed to get X user profile: " + response.getBody());
        }

        JsonObject jsonResponse = gson.fromJson(response.getBody(), JsonObject.class);
        JsonObject userData = jsonResponse.getAsJsonObject("data");

        return new XUserProfile(
            userData.get("id").getAsString(),
            userData.get("username").getAsString(),
            userData.has("name") ? userData.get("name").getAsString() : null
        );
    }

    /**
     * Finds existing user or creates new one based on X profile.
     */
    private User findOrCreateUser(XUserProfile profile) throws SQLException {
        // First try to find existing user by X user ID
        User existingUser = findUserByXUserId(profile.id);

        if (existingUser != null) {
            // Update profile information if it changed
            if (!profile.username.equals(existingUser.xUsername()) ||
                !nullSafeEquals(profile.name, existingUser.xDisplayName())) {

                User updatedUser = existingUser.withUpdatedXProfile(profile.username, profile.name);
                updateUser(updatedUser);
                LoggerUtil.info("Updated X profile for user: " + existingUser.id());
                return updatedUser;
            }
            return existingUser;
        }

        // Create new user
        User newUser = User.createNewXUser(profile.id, profile.username, profile.name);
        User savedUser = createUser(newUser);
        LoggerUtil.info("Created new user account for X user: @" + profile.username);
        return savedUser;
    }

    /**
     * Finds user by X user ID.
     */
    private User findUserByXUserId(String xUserId) throws SQLException {
        String sql = "SELECT id, auth_provider, x_user_id, x_username, x_display_name, " +
                    "discord_user_id, discord_username, discord_display_name, created_at, is_active " +
                    "FROM users WHERE x_user_id = ? AND is_active = 1";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, xUserId);
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
        String sql = "UPDATE users SET x_username = ?, x_display_name = ? WHERE id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.xUsername());
            stmt.setString(2, user.xDisplayName());
            stmt.setInt(3, user.id());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("Failed to update user - no rows affected");
            }
        }
    }

    /**
     * X user profile data from API.
     */
    public static record XUserProfile(String id, String username, String name) {}
}
