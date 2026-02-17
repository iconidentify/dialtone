/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.HttpClientConfig;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.HttpBasicAuthenticationScheme;

/**
 * ScribeJava API implementation for X (Twitter) OAuth 2.0.
 *
 * X uses OAuth 2.0 with PKCE (Proof Key for Code Exchange) for security.
 * This implementation provides the necessary endpoints and configuration
 * for authenticating with the X API v2.
 *
 * References:
 * - https://developer.twitter.com/en/docs/authentication/oauth-2-0/authorization-code
 * - https://datatracker.ietf.org/doc/html/rfc7636 (PKCE specification)
 */
public class XApi20 extends DefaultApi20 {

    /**
     * X OAuth 2.0 authorization endpoint.
     * This is where users are redirected to authorize the application.
     */
    private static final String AUTHORIZATION_URL =
        "https://twitter.com/i/oauth2/authorize";

    /**
     * X OAuth 2.0 token endpoint.
     * Used to exchange authorization code for access tokens.
     */
    private static final String ACCESS_TOKEN_ENDPOINT =
        "https://api.twitter.com/2/oauth2/token";

    /**
     * X API v2 user info endpoint.
     * Used to get authenticated user profile information.
     */
    public static final String USER_INFO_ENDPOINT =
        "https://api.twitter.com/2/users/me";

    /**
     * Private constructor to prevent direct instantiation.
     * Use XApi20.instance() instead.
     */
    protected XApi20() {
        // Singleton pattern - use instance() method
    }

    /**
     * Thread-safe singleton instance holder.
     */
    private static class InstanceHolder {
        private static final XApi20 INSTANCE = new XApi20();
    }

    /**
     * Gets the singleton instance of XApi20.
     *
     * @return The singleton XApi20 instance
     */
    public static XApi20 instance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Returns the access token endpoint for X OAuth 2.0.
     *
     * @return The token endpoint URL
     */
    @Override
    public String getAccessTokenEndpoint() {
        return ACCESS_TOKEN_ENDPOINT;
    }

    /**
     * Returns the authorization base URL for X OAuth 2.0.
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
     * X OAuth 2.0 requires HTTP Basic Authentication with client credentials
     * in the Authorization header as per RFC 6749.
     *
     * @return HTTP Basic authentication scheme
     */
    @Override
    public ClientAuthentication getClientAuthentication() {
        return HttpBasicAuthenticationScheme.instance();
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
     * Returns the default OAuth 2.0 scopes for X API access.
     * These scopes provide basic read access to user information and tweets.
     *
     * @return Default OAuth scopes
     */
    public String getDefaultScopes() {
        return "tweet.read users.read offline.access";
    }

    /**
     * Returns a human-readable name for this API.
     *
     * @return API display name
     */
    @Override
    public String toString() {
        return "X (Twitter) OAuth 2.0 API";
    }
}