/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.auth;

/**
 * Immutable record containing login credentials extracted from Dd frame.
 */
public record LoginCredentials(String username, String password) {

    public LoginCredentials {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
    }
}
