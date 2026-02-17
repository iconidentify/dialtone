/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import com.dialtone.protocol.auth.UserAuthenticator;
import com.dialtone.utils.LoggerUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Authenticates users based on a property file configuration.
 * Supports multiple users configured as: auth.users=user1:pass1,user2:pass2,user3:pass3
 */
public class PropertyBasedUserAuthenticator implements UserAuthenticator {

    private final Map<String, String> validUsers;

    public PropertyBasedUserAuthenticator(Properties properties) {
        this.validUsers = new HashMap<>();
        parseUsers(properties);
    }

    /**
     * Parse users from properties file.
     * Expected format: auth.users=user1:pass1,user2:pass2,user3:pass3
     */
    private void parseUsers(Properties properties) {
        String usersProperty = properties.getProperty("auth.users", "");

        if (usersProperty.isEmpty()) {
            LoggerUtil.warn("No users configured in auth.users property");
            return;
        }

        String[] userEntries = usersProperty.split(",");

        for (String entry : userEntries) {
            String trimmedEntry = entry.trim();

            if (trimmedEntry.isEmpty()) {
                continue;
            }

            String[] parts = trimmedEntry.split(":", 2);

            if (parts.length != 2) {
                LoggerUtil.warn("Invalid user entry (expected username:password): " + trimmedEntry);
                continue;
            }

            String username = parts[0].trim();
            String password = parts[1].trim();

            if (username.isEmpty() || password.isEmpty()) {
                LoggerUtil.warn("Skipping user entry with empty username or password: " + trimmedEntry);
                continue;
            }

            // Store username in case-insensitive manner (lowercase key)
            validUsers.put(username.toLowerCase(), password);
            LoggerUtil.info("Registered user: " + username);
        }

        LoggerUtil.info("PropertyBasedUserAuthenticator initialized with " + validUsers.size() + " users");
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        // Case-insensitive username lookup
        String storedPassword = validUsers.get(username.toLowerCase());

        if (storedPassword == null) {
            return false;
        }

        // Password is case-sensitive
        return storedPassword.equals(password);
    }

    /**
     * Get the number of registered users.
     * Useful for testing and diagnostics.
     */
    public int getUserCount() {
        return validUsers.size();
    }

    /**
     * Check if a username is registered (case-insensitive).
     * Useful for testing and diagnostics.
     */
    public boolean hasUser(String username) {
        if (username == null) {
            return false;
        }
        return validUsers.containsKey(username.toLowerCase());
    }
}
