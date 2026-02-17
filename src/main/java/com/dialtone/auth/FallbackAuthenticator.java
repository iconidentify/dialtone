/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import com.dialtone.protocol.auth.UserAuthenticator;
import com.dialtone.utils.LoggerUtil;

/**
 * Authenticator that provides fallback to ephemeral guest sessions.
 *
 * Authentication flow:
 * 1. Try database authentication first
 * 2. If DB auth succeeds -> return success (registered user)
 * 3. If DB auth fails AND ephemeral fallback enabled -> generate guest session
 * 4. If DB auth fails AND ephemeral fallback disabled -> return auth failure
 *
 * This replaces the "auth pass through" mode used for local testing,
 * and is configurable via the auth.ephemeral.fallback.enabled property.
 */
public class FallbackAuthenticator implements UserAuthenticator {

    private final DatabaseUserAuthenticator dbAuth;
    private final EphemeralUserManager ephemeralManager;
    private final boolean ephemeralFallbackEnabled;

    /**
     * Create a fallback authenticator.
     *
     * @param dbAuth The database authenticator for registered users
     * @param ephemeralManager Manager for ephemeral guest sessions
     * @param ephemeralFallbackEnabled If true, failed auth creates ephemeral session;
     *                                  if false, failed auth returns error
     */
    public FallbackAuthenticator(DatabaseUserAuthenticator dbAuth,
                                  EphemeralUserManager ephemeralManager,
                                  boolean ephemeralFallbackEnabled) {
        this.dbAuth = dbAuth;
        this.ephemeralManager = ephemeralManager;
        this.ephemeralFallbackEnabled = ephemeralFallbackEnabled;

        LoggerUtil.info("FallbackAuthenticator initialized, ephemeralFallback=" + ephemeralFallbackEnabled);
    }

    /**
     * Authenticate with full result information.
     *
     * This is the primary authentication method that supports ephemeral fallback.
     * Use this method instead of the boolean authenticate() for full functionality.
     *
     * @param username The screenname to authenticate
     * @param password The password to verify
     * @return AuthResult with success/failure status, screenname, and ephemeral flag
     */
    public AuthResult authenticateWithResult(String username, String password) {
        // Input validation
        if (username == null || username.trim().isEmpty()) {
            LoggerUtil.debug("Authentication failed: empty username");
            if (ephemeralFallbackEnabled) {
                return createEphemeralSession("empty_username");
            }
            return AuthResult.failure("Username is required");
        }

        if (password == null) {
            LoggerUtil.debug("Authentication failed: null password");
            if (ephemeralFallbackEnabled) {
                return createEphemeralSession(username);
            }
            return AuthResult.failure("Password is required");
        }

        // Try database authentication first
        boolean dbAuthSuccess = dbAuth.authenticate(username, password);

        if (dbAuthSuccess) {
            LoggerUtil.info("Database authentication successful for: " + username);
            return AuthResult.success(username);
        }

        // Database auth failed
        LoggerUtil.debug("Database authentication failed for: " + username);

        // Check if ephemeral fallback is enabled
        if (!ephemeralFallbackEnabled) {
            LoggerUtil.info("Ephemeral fallback disabled, returning auth failure for: " + username);
            return AuthResult.failure("Invalid screenname or password");
        }

        // Create ephemeral guest session
        return createEphemeralSession(username);
    }

    /**
     * Create an ephemeral guest session.
     *
     * @param attemptedUsername The username that was attempted (for logging)
     * @return AuthResult with ephemeral success
     */
    private AuthResult createEphemeralSession(String attemptedUsername) {
        String guestName = ephemeralManager.generateGuestName();
        LoggerUtil.info("Created ephemeral session '" + guestName +
                       "' for failed auth attempt on: " + attemptedUsername);
        return AuthResult.ephemeralSuccess(guestName);
    }

    /**
     * Legacy authenticate method for backward compatibility.
     *
     * This method implements the UserAuthenticator interface.
     * When ephemeral fallback is enabled, this will always return true
     * (since failed auth becomes ephemeral success).
     *
     * For full functionality, use authenticateWithResult() instead.
     *
     * @param username The screenname to authenticate
     * @param password The password to verify
     * @return true if authentication succeeds (or ephemeral fallback creates session)
     */
    @Override
    public boolean authenticate(String username, String password) {
        AuthResult result = authenticateWithResult(username, password);
        return result.isSuccess();
    }

    /**
     * Get the ephemeral user manager.
     *
     * @return The EphemeralUserManager instance
     */
    public EphemeralUserManager getEphemeralManager() {
        return ephemeralManager;
    }

    /**
     * Check if ephemeral fallback is enabled.
     *
     * @return true if ephemeral fallback is enabled
     */
    public boolean isEphemeralFallbackEnabled() {
        return ephemeralFallbackEnabled;
    }
}
