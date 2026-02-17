/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

/**
 * Result of an authentication attempt.
 * Supports both successful registered user authentication and ephemeral guest sessions.
 */
public class AuthResult {
    private final boolean success;
    private final String screenname;
    private final boolean ephemeral;
    private final String errorMessage;

    private AuthResult(boolean success, String screenname, boolean ephemeral, String errorMessage) {
        this.success = success;
        this.screenname = screenname;
        this.ephemeral = ephemeral;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful authentication result for a registered user.
     *
     * @param screenname The authenticated screenname
     * @return AuthResult indicating success
     */
    public static AuthResult success(String screenname) {
        return new AuthResult(true, screenname, false, null);
    }

    /**
     * Create a successful authentication result for an ephemeral guest user.
     * The screenname will be a generated guest name (e.g., ~Guest1234).
     *
     * @param guestName The generated guest screenname
     * @return AuthResult indicating ephemeral success
     */
    public static AuthResult ephemeralSuccess(String guestName) {
        return new AuthResult(true, guestName, true, null);
    }

    /**
     * Create a failed authentication result.
     *
     * @param message Error message describing the failure
     * @return AuthResult indicating failure
     */
    public static AuthResult failure(String message) {
        return new AuthResult(false, null, false, message);
    }

    /**
     * Check if authentication was successful (either registered or ephemeral).
     *
     * @return true if authentication succeeded
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the authenticated screenname.
     * For ephemeral users, this is the generated guest name.
     * For registered users, this is their registered screenname.
     *
     * @return The screenname, or null if authentication failed
     */
    public String getScreenname() {
        return screenname;
    }

    /**
     * Check if this is an ephemeral (guest) session.
     * Ephemeral sessions are automatically cleaned up on disconnect.
     *
     * @return true if this is an ephemeral session
     */
    public boolean isEphemeral() {
        return ephemeral;
    }

    /**
     * Get the error message if authentication failed.
     *
     * @return Error message, or null if authentication succeeded
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("AuthResult[success=true, screenname=%s, ephemeral=%s]",
                    screenname, ephemeral);
        } else {
            return String.format("AuthResult[success=false, error=%s]", errorMessage);
        }
    }
}
