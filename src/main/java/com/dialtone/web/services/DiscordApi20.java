/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;

/**
 * ScribeJava API implementation for Discord OAuth 2.0.
 *
 * Discord uses OAuth 2.0 with PKCE (Proof Key for Code Exchange) for security.
 * This implementation provides the necessary endpoints and configuration
 * for authenticating with the Discord API.
 *
 * References:
 * - https://discord.com/developers/docs/topics/oauth2
 * - https://datatracker.ietf.org/doc/html/rfc7636 (PKCE specification)
 */
public class DiscordApi20 extends DefaultApi20 {

    /**
     * Discord OAuth 2.0 authorization endpoint.
     * This is where users are redirected to authorize the application.
     */
    private static final String AUTHORIZATION_URL =
        "https://discord.com/oauth2/authorize";

    /**
     * Discord OAuth 2.0 token endpoint.
     * Used to exchange authorization code for access tokens.
     */
    private static final String ACCESS_TOKEN_ENDPOINT =
        "https://discord.com/api/oauth2/token";

    /**
     * Discord API user info endpoint.
     * Used to get authenticated user profile information.
     */
    public static final String USER_INFO_ENDPOINT =
        "https://discord.com/api/users/@me";

    /**
     * Private constructor to prevent direct instantiation.
     * Use DiscordApi20.instance() instead.
     */
    protected DiscordApi20() {
        // Singleton pattern - use instance() method
    }

    /**
     * Thread-safe singleton instance holder.
     */
    private static class InstanceHolder {
        private static final DiscordApi20 INSTANCE = new DiscordApi20();
    }

    /**
     * Gets the singleton instance of DiscordApi20.
     *
     * @return The singleton DiscordApi20 instance
     */
    public static DiscordApi20 instance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Returns the access token endpoint for Discord OAuth 2.0.
     *
     * @return The token endpoint URL
     */
    @Override
    public String getAccessTokenEndpoint() {
        return ACCESS_TOKEN_ENDPOINT;
    }

    /**
     * Returns the authorization base URL for Discord OAuth 2.0.
     * ScribeJava will append the necessary parameters (client_id, redirect_uri, etc.).
     *
     * @return The authorization endpoint URL
     */
    @Override
    protected String getAuthorizationBaseUrl() {
        return AUTHORIZATION_URL;
    }

    /**
     * Specifies the client authentication method for token requests.
     * Discord OAuth 2.0 requires client credentials in the request body
     * (not HTTP Basic Authentication like X/Twitter).
     *
     * @return Request body authentication scheme
     */
    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }

    /**
     * Gets the user info endpoint URL.
     * This is not part of the standard OAuth 2.0 flow but is commonly used
     * to retrieve user profile information after authentication.
     *
     * @return The user info endpoint URL
     */
    public String getUserInfoEndpoint() {
        return USER_INFO_ENDPOINT;
    }

    /**
     * Returns the default OAuth 2.0 scopes for Discord API access.
     * 'identify' provides basic user info (id, username, discriminator, avatar).
     *
     * @return Default OAuth scopes
     */
    public String getDefaultScopes() {
        return "identify";
    }

    /**
     * Returns a human-readable name for this API.
     *
     * @return API display name
     */
    @Override
    public String toString() {
        return "Discord OAuth 2.0 API";
    }
}

