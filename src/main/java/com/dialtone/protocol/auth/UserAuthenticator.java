/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.auth;

/**
 * Interface for user authentication.
 * Allows for different authentication implementations (hardcoded, database, LDAP, etc.).
 */
public interface UserAuthenticator {

    /**
     * Authenticate a user with username and password.
     *
     * @param username User's screen name (1-16 characters)
     * @param password User's password (1-8 characters)
     * @return true if authentication succeeds, false otherwise
     */
    boolean authenticate(String username, String password);

    /**
     * Validate username format.
     *
     * @param username Screen name to validate
     * @return true if valid (1-16 chars), false otherwise
     */
    default boolean isValidUsername(String username) {
        return username != null && username.length() >= 1 && username.length() <= 16;
    }

    /**
     * Validate password format.
     *
     * @param password Password to validate
     * @return true if valid (1-8 chars), false otherwise
     */
    default boolean isValidPassword(String password) {
        return password != null && password.length() >= 1 && password.length() <= 8;
    }
}
