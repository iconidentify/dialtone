/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for user management operations.
 *
 * Provides CRUD operations for users, supporting admin functionality
 * like listing all users, deactivating accounts, and bulk operations.
 */
public class UserService {

    private final DatabaseManager databaseManager;

    public UserService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Gets all users with pagination and optional filtering.
     *
     * @param limit Maximum number of users to return
     * @param offset Number of users to skip (for pagination)
     * @param activeOnly If true, only return active users
     * @return List of users
     */
    public List<User> getAllUsers(int limit, int offset, boolean activeOnly) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT id, auth_provider, x_user_id, x_username, x_display_name,
                   discord_user_id, discord_username, discord_display_name, email, created_at, is_active
            FROM users
        """);

        List<Object> parameters = new ArrayList<>();

        if (activeOnly) {
            sqlBuilder.append(" WHERE is_active = 1");
        }

        sqlBuilder.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<User> users = new ArrayList<>();

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                users.add(userFromResultSet(rs));
            }
        }

        LoggerUtil.debug(String.format("Retrieved %d users (limit=%d, offset=%d, activeOnly=%b)",
                        users.size(), limit, offset, activeOnly));
        return users;
    }

    /**
     * Gets total count of users.
     *
     * @param activeOnly If true, only count active users
     * @return Total user count
     */
    public int getUserCount(boolean activeOnly) throws SQLException {
        String sql = activeOnly
            ? "SELECT COUNT(*) FROM users WHERE is_active = 1"
            : "SELECT COUNT(*) FROM users";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Gets total count of screennames across all users.
     *
     * @return Total screenname count
     */
    public int getScreennameCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM screennames";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Gets a user by ID.
     *
     * @param userId User ID
     * @return User or null if not found
     */
    public User getUserById(int userId) throws SQLException {
        String sql = """
            SELECT id, auth_provider, x_user_id, x_username, x_display_name,
                   discord_user_id, discord_username, discord_display_name, email, created_at, is_active
            FROM users WHERE id = ?
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return userFromResultSet(rs);
            }
        }

        return null;
    }

    /**
     * Helper method to create a User from a ResultSet.
     */
    private User userFromResultSet(ResultSet rs) throws SQLException {
        String authProvider = rs.getString("auth_provider");
        if (authProvider == null) {
            authProvider = User.PROVIDER_X; // Default for legacy users
        }
        
        return User.fromDatabase(
            rs.getInt("id"),
            authProvider,
            rs.getString("x_user_id"),
            rs.getString("x_username"),
            rs.getString("x_display_name"),
            rs.getString("discord_user_id"),
            rs.getString("discord_username"),
            rs.getString("discord_display_name"),
            rs.getString("email"),
            LocalDateTime.parse(rs.getString("created_at").replace(" ", "T")),
            rs.getBoolean("is_active")
        );
    }

    /**
     * Updates a user's active status.
     *
     * @param userId User ID
     * @param active New active status
     * @return Updated user
     * @throws UserServiceException if user not found or update fails
     */
    public User updateUserStatus(int userId, boolean active) throws SQLException, UserServiceException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, active);
            stmt.setInt(2, userId);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new UserServiceException("User not found: " + userId);
            }
        }

        // Return updated user
        User updatedUser = getUserById(userId);
        if (updatedUser == null) {
            throw new UserServiceException("Failed to retrieve updated user: " + userId);
        }

        LoggerUtil.info(String.format("Updated user %d status to active=%b", userId, active));
        return updatedUser;
    }

    /**
     * Gets all screennames for a user.
     *
     * @param userId User ID
     * @return List of screennames
     */
    public List<Screenname> getScreennamesForUser(int userId) throws SQLException {
        String sql = """
            SELECT id, user_id, screenname, password_hash, is_primary, created_at
            FROM screennames WHERE user_id = ?
            ORDER BY is_primary DESC, created_at ASC
        """;

        List<Screenname> screennames = new ArrayList<>();

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                screennames.add(Screenname.fromDatabase(
                    rs.getInt("id"),
                    rs.getInt("user_id"),
                    rs.getString("screenname"),
                    rs.getString("password_hash"),
                    rs.getBoolean("is_primary"),
                    LocalDateTime.parse(rs.getString("created_at").replace(" ", "T"))
                ));
            }
        }

        return screennames;
    }

    /**
     * Deletes a user and all associated screennames.
     *
     * @param userId User ID to delete
     * @throws UserServiceException if user not found or deletion fails
     */
    public void deleteUser(int userId) throws SQLException, UserServiceException {
        User user = getUserById(userId);
        if (user == null) {
            throw new UserServiceException("User not found: " + userId);
        }

        try (Connection conn = databaseManager.getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Delete screennames first (foreign key constraint)
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM screennames WHERE user_id = ?")) {
                    stmt.setInt(1, userId);
                    int screennamesDeleted = stmt.executeUpdate();
                    LoggerUtil.debug(String.format("Deleted %d screennames for user %d", screennamesDeleted, userId));
                }

                // Delete user roles
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
                    stmt.setInt(1, userId);
                    int rolesDeleted = stmt.executeUpdate();
                    LoggerUtil.debug(String.format("Deleted %d roles for user %d", rolesDeleted, userId));
                }

                // Delete user
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    stmt.setInt(1, userId);
                    int rowsDeleted = stmt.executeUpdate();

                    if (rowsDeleted == 0) {
                        throw new UserServiceException("Failed to delete user: " + userId);
                    }
                }

                conn.commit();
                LoggerUtil.info(String.format("Deleted user %d (%s) and all associated data", userId, user.getProviderUsername()));

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Creates a new user manually (admin action).
     *
     * @param xUserId External X user identifier (optional, generated if missing)
     * @param xUsername X username/handle (required)
     * @param xDisplayName Display name (defaults to username)
     * @param isActive Whether the account should start active
     * @return Created user record
     */
    public User createManualUser(String xUserId, String xUsername, String xDisplayName, boolean isActive)
            throws SQLException, UserServiceException {

        String finalUsername = (xUsername == null || xUsername.trim().isEmpty())
            ? generateSystemUsername()
            : xUsername.trim();

        String finalDisplayName = (xDisplayName == null || xDisplayName.trim().isEmpty())
            ? finalUsername
            : xDisplayName.trim();

        String finalXUserId = (xUserId == null || xUserId.trim().isEmpty())
            ? "manual-" + UUID.randomUUID()
            : xUserId.trim();

        String sql = """
            INSERT INTO users (auth_provider, x_user_id, x_username, x_display_name, created_at, is_active)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?) RETURNING id
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, User.PROVIDER_X); // Manual users default to X provider
            stmt.setString(2, finalXUserId);
            stmt.setString(3, finalUsername);
            stmt.setString(4, finalDisplayName);
            stmt.setBoolean(5, isActive);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                LoggerUtil.info(String.format("Manually created user %d (@%s)", id, finalUsername));
                return getUserById(id);
            }

            throw new UserServiceException("Failed to create user record");

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new UserServiceException("X user ID or username already exists", e);
            }
            throw e;
        }
    }

    private String generateSystemUsername() {
        return "dialtone_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /**
     * Exception thrown by UserService operations.
     */
    public static class UserServiceException extends Exception {
        public UserServiceException(String message) {
            super(message);
        }

        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}