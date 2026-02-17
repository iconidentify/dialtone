/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import com.dialtone.db.DatabaseManager;
import com.dialtone.protocol.auth.UserAuthenticator;
import com.dialtone.utils.LoggerUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Authenticates users against the SQLite database containing screennames.
 *
 * This authenticator allows the Dialtone server to authenticate users
 * using screennames and passwords stored in the web interface database.
 * Supports case-insensitive username lookup and BCrypt password verification.
 */
public class DatabaseUserAuthenticator implements UserAuthenticator {

    private final DatabaseManager databaseManager;

    /**
     * Creates a new database-backed user authenticator.
     *
     * @param databaseManager Database connection manager
     */
    public DatabaseUserAuthenticator(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        LoggerUtil.info("DatabaseUserAuthenticator initialized");
    }

    /**
     * Creates a database authenticator using the singleton DatabaseManager.
     * Uses the default database path from configuration.
     *
     * @param dbPath Path to the SQLite database file
     */
    public DatabaseUserAuthenticator(String dbPath) {
        this.databaseManager = DatabaseManager.getInstance(dbPath);
        LoggerUtil.info("DatabaseUserAuthenticator initialized with database: " + dbPath);
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            LoggerUtil.debug("Authentication failed: null username or password");
            return false;
        }

        if (!isValidUsername(username) || !isValidPassword(password)) {
            LoggerUtil.debug("Authentication failed: invalid username or password format");
            return false;
        }

        try {
            // Look up screenname in database (case-insensitive)
            String passwordHash = getPasswordHashForScreenname(username);

            if (passwordHash == null) {
                LoggerUtil.debug("Authentication failed: screenname '" + username + "' not found");
                return false;
            }

            // Verify password using BCrypt
            boolean isValid = BCrypt.checkpw(password, passwordHash);

            if (isValid) {
                LoggerUtil.info("Successful authentication for screenname: " + username);
            } else {
                LoggerUtil.debug("Authentication failed: invalid password for screenname '" + username + "'");
            }

            return isValid;

        } catch (SQLException e) {
            LoggerUtil.error("Database error during authentication for '" + username + "': " + e.getMessage());
            return false;
        } catch (Exception e) {
            LoggerUtil.error("Unexpected error during authentication for '" + username + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the password hash for a screenname from the database.
     * Performs case-insensitive lookup to match protocol behavior.
     *
     * @param screenname Screenname to look up
     * @return BCrypt password hash, or null if screenname not found
     * @throws SQLException if database query fails
     */
    private String getPasswordHashForScreenname(String screenname) throws SQLException {
        String sql = """
            SELECT s.password_hash
            FROM screennames s
            INNER JOIN users u ON s.user_id = u.id
            WHERE LOWER(s.screenname) = LOWER(?)
            AND u.is_active = 1
            """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, screenname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password_hash");
            }

            return null; // Screenname not found
        }
    }

    /**
     * Checks if a screenname exists in the database.
     * Useful for testing and diagnostics.
     *
     * @param screenname Screenname to check (case-insensitive)
     * @return true if screenname exists and user is active, false otherwise
     */
    public boolean hasScreenname(String screenname) {
        if (screenname == null) {
            return false;
        }

        try {
            return getPasswordHashForScreenname(screenname) != null;
        } catch (SQLException e) {
            LoggerUtil.error("Error checking if screenname exists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the count of active screennames in the database.
     * Useful for testing and diagnostics.
     *
     * @return Number of active screennames, or -1 if query fails
     */
    public int getScreennameCount() {
        String sql = """
            SELECT COUNT(*)
            FROM screennames s
            INNER JOIN users u ON s.user_id = u.id
            WHERE u.is_active = 1
            """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LoggerUtil.error("Error getting screenname count: " + e.getMessage());
        }

        return -1; // Error occurred
    }

    /**
     * Gets information about a screenname for diagnostic purposes.
     *
     * @param screenname Screenname to get info for
     * @return ScreennameInfo object with details, or null if not found
     */
    public ScreennameInfo getScreennameInfo(String screenname) {
        if (screenname == null) {
            return null;
        }

        String sql = """
            SELECT s.id, s.screenname, s.is_primary, s.created_at,
                   u.id as user_id, u.x_username, u.x_display_name
            FROM screennames s
            INNER JOIN users u ON s.user_id = u.id
            WHERE LOWER(s.screenname) = LOWER(?)
            AND u.is_active = 1
            """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, screenname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String createdAtRaw = rs.getString("created_at");
                LocalDateTime createdAt;
                if (createdAtRaw != null && !createdAtRaw.isEmpty()) {
                    createdAt = LocalDateTime.parse(createdAtRaw.replace(" ", "T"));
                } else {
                    createdAt = LocalDateTime.now();
                }

                return new ScreennameInfo(
                    rs.getInt("id"),
                    rs.getString("screenname"),
                    rs.getBoolean("is_primary"),
                    createdAt,
                    rs.getInt("user_id"),
                    rs.getString("x_username"),
                    rs.getString("x_display_name")
                );
            }

        } catch (SQLException e) {
            LoggerUtil.error("Error getting screenname info: " + e.getMessage());
        }

        return null;
    }

    /**
     * Validates username format for authentication.
     * Must be 1-10 characters, alphanumeric only.
     * Rejects reserved prefix (~) which is for ephemeral guests only.
     */
    @Override
    public boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }

        // Check length
        if (username.length() < 1 || username.length() > 10) {
            return false;
        }

        // Reject reserved prefix (ephemeral guests only)
        if (username.startsWith("~")) {
            return false;
        }

        // Check for alphanumeric characters only
        return username.matches("^[a-zA-Z0-9]+$");
    }

    /**
     * Record containing diagnostic information about a screenname.
     */
    public record ScreennameInfo(
        int id,
        String screenname,
        boolean isPrimary,
        java.time.LocalDateTime createdAt,
        int userId,
        String xUsername,
        String xDisplayName
    ) {}
}