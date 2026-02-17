/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing screennames linked to user accounts.
 *
 * Handles screenname creation, updates, password changes, and primary
 * screenname management with business rule enforcement.
 */
public class ScreennameService {
    private final DatabaseManager databaseManager;
    private final AdminSecurityService adminSecurityService;
    private final UserService userService;

    // Business rule constants
    public static final int MAX_SCREENNAMES_PER_USER = 2;  // Allow up to 2 screennames per normal user
    public static final int MAX_SCREENNAMES_PER_ADMIN = 20; // Allow up to 20 screennames per admin user
    public static final int BCRYPT_ROUNDS = 12;

    public ScreennameService(DatabaseManager databaseManager, AdminSecurityService adminSecurityService, UserService userService) {
        this.databaseManager = databaseManager;
        this.adminSecurityService = adminSecurityService;
        this.userService = userService;
    }

    /**
     * Gets all screennames for a user.
     *
     * @param userId User ID to get screennames for
     * @return List of user's screennames (empty list if none)
     */
    public List<Screenname> getScreennamesForUser(int userId) throws ScreennameServiceException {
        String sql = "SELECT id, user_id, screenname, password_hash, is_primary, created_at " +
                    "FROM screennames WHERE user_id = ? ORDER BY created_at ASC";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            List<Screenname> screennames = new ArrayList<>();
            while (rs.next()) {
                // SQLite stores timestamps as TEXT in ISO format
                String createdAtStr = rs.getString("created_at");
                LocalDateTime createdAtLocal;
                if (createdAtStr != null && !createdAtStr.isEmpty()) {
                    // Parse SQLite's ISO format timestamp
                    createdAtLocal = LocalDateTime.parse(createdAtStr.replace(" ", "T"));
                } else {
                    createdAtLocal = LocalDateTime.now();
                }
                
                screennames.add(Screenname.fromDatabase(
                    rs.getInt("id"),
                    rs.getInt("user_id"),
                    rs.getString("screenname"),
                    rs.getString("password_hash"),
                    rs.getBoolean("is_primary"),
                    createdAtLocal
                ));
            }

            return screennames;

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get screennames for user " + userId + ": " + e.getMessage());
            throw new ScreennameServiceException("Failed to retrieve screennames", e);
        }
    }

    /**
     * Determines the maximum number of screennames allowed for a user based on their role.
     *
     * @param userId User ID to check
     * @return Maximum screennames allowed (2 for normal users, 20 for admin users)
     * @throws ScreennameServiceException if user lookup fails
     */
    private int getMaxScreennamesForUser(int userId) throws ScreennameServiceException {
        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                throw new ScreennameServiceException("User not found: " + userId);
            }

            return adminSecurityService.isAdmin(user) ? MAX_SCREENNAMES_PER_ADMIN : MAX_SCREENNAMES_PER_USER;
        } catch (Exception e) {
            LoggerUtil.error("Failed to determine user role for user " + userId + ": " + e.getMessage());
            // Default to normal user limit for security
            return MAX_SCREENNAMES_PER_USER;
        }
    }

    /**
     * Creates a new screenname for a user.
     * Validates business rules: role-based limits, unique screenname, valid format.
     *
     * @param userId User ID to create screenname for
     * @param screenname Desired screenname (1-10 chars, alphanumeric)
     * @param password Password (1-8 chars for protocol compatibility)
     * @return Created Screenname object
     * @throws ScreennameServiceException if validation fails or creation fails
     */
    public Screenname createScreenname(int userId, String screenname, String password)
            throws ScreennameServiceException {

        // Validate inputs
        if (!Screenname.isValidScreenname(screenname)) {
            throw new ScreennameServiceException(
                "Invalid screenname format. Must be 1-10 alphanumeric characters.");
        }

        if (!Screenname.isValidPassword(password)) {
            throw new ScreennameServiceException(
                "Invalid password format. Must be 1-8 characters.");
        }

        try {
            // Check user doesn't exceed max screennames (role-based limit)
            List<Screenname> existingScreennames = getScreennamesForUser(userId);
            int maxScreennames = getMaxScreennamesForUser(userId);
            if (existingScreennames.size() >= maxScreennames) {
                String limitMessage = maxScreennames == MAX_SCREENNAMES_PER_ADMIN ?
                    String.format("You have reached the maximum number of screennames allowed for admin accounts (%d).", MAX_SCREENNAMES_PER_ADMIN) :
                    String.format("You have reached the maximum number of screennames allowed per account (%d).", MAX_SCREENNAMES_PER_USER);
                throw new ScreennameServiceException(limitMessage);
            }

            // Check screenname is available
            if (isScreennameTaken(screenname)) {
                throw new ScreennameServiceException("Screenname '" + screenname + "' is already taken");
            }

            // Hash password
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));

            // Only the first screenname should be primary
            boolean isPrimary = existingScreennames.isEmpty();

            // Create screenname
            Screenname newScreenname = Screenname.createNew(userId, screenname, passwordHash, isPrimary);
            Screenname savedScreenname = insertScreenname(newScreenname);

            LoggerUtil.info("Created screenname '" + screenname + "' for user " + userId +
                          " (primary: " + isPrimary + ")");

            return savedScreenname;

        } catch (ScreennameServiceException e) {
            throw e; // Re-throw business logic exceptions
        } catch (Exception e) {
            LoggerUtil.error("Unexpected error creating screenname: " + e.getMessage());
            throw new ScreennameServiceException("Failed to create screenname", e);
        }
    }

    /**
     * Updates a screenname (changes the actual screenname text).
     * Validates the new screenname is available and belongs to the user.
     *
     * @param screennameId ID of screenname to update
     * @param userId User ID for authorization
     * @param newScreenname New screenname text
     * @return Updated Screenname object
     */
    public Screenname updateScreenname(int screennameId, int userId, String newScreenname)
            throws ScreennameServiceException {

        if (!Screenname.isValidScreenname(newScreenname)) {
            throw new ScreennameServiceException(
                "Invalid screenname format. Must be 1-10 alphanumeric characters.");
        }

        try {
            // Get existing screenname
            Screenname existing = getScreennameById(screennameId, userId);

            // Check if screenname actually changed
            if (existing.screenname().equals(newScreenname)) {
                return existing; // No change needed
            }

            // Check new screenname is available
            if (isScreennameTaken(newScreenname)) {
                throw new ScreennameServiceException("Screenname '" + newScreenname + "' is already taken");
            }

            // Update screenname
            String sql = "UPDATE screennames SET screenname = ? WHERE id = ? AND user_id = ?";

            try (Connection conn = databaseManager.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newScreenname);
                stmt.setInt(2, screennameId);
                stmt.setInt(3, userId);

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new ScreennameServiceException("Screenname not found or access denied");
                }

                LoggerUtil.info("Updated screenname " + screennameId + " to '" + newScreenname + "'");
                return existing.withScreenname(newScreenname);
            }

        } catch (ScreennameServiceException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to update screenname: " + e.getMessage());
            throw new ScreennameServiceException("Failed to update screenname", e);
        }
    }

    /**
     * Changes password for a screenname.
     *
     * @param screennameId ID of screenname to update password for
     * @param userId User ID for authorization
     * @param newPassword New password (1-8 chars)
     * @return Updated Screenname object
     */
    public Screenname updatePassword(int screennameId, int userId, String newPassword)
            throws ScreennameServiceException {

        if (!Screenname.isValidPassword(newPassword)) {
            throw new ScreennameServiceException(
                "Invalid password format. Must be 1-8 characters.");
        }

        try {
            // Get existing screenname
            Screenname existing = getScreennameById(screennameId, userId);

            // Hash new password
            String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(BCRYPT_ROUNDS));

            // Update password
            String sql = "UPDATE screennames SET password_hash = ? WHERE id = ? AND user_id = ?";

            try (Connection conn = databaseManager.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newPasswordHash);
                stmt.setInt(2, screennameId);
                stmt.setInt(3, userId);

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new ScreennameServiceException("Screenname not found or access denied");
                }

                LoggerUtil.info("Updated password for screenname " + screennameId);
                return existing.withPasswordHash(newPasswordHash);
            }

        } catch (ScreennameServiceException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to update password: " + e.getMessage());
            throw new ScreennameServiceException("Failed to update password", e);
        }
    }

    /**
     * Sets a screenname as the user's primary screenname.
     * Only one screenname per user can be primary.
     *
     * @param screennameId ID of screenname to make primary
     * @param userId User ID for authorization
     * @return Updated Screenname object
     */
    public Screenname setPrimary(int screennameId, int userId) throws ScreennameServiceException {
        try (Connection conn = databaseManager.getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Verify screenname belongs to user
                Screenname screenname = getScreennameById(screennameId, userId);

                // Always ensure only one primary exists, even if the target is already primary
                // Clear existing primary for this user
                String clearPrimarySql = "UPDATE screennames SET is_primary = 0 WHERE user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(clearPrimarySql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Set new primary
                String setPrimarySql = "UPDATE screennames SET is_primary = 1 WHERE id = ? AND user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(setPrimarySql)) {
                    stmt.setInt(1, screennameId);
                    stmt.setInt(2, userId);

                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated == 0) {
                        throw new ScreennameServiceException("Screenname not found or access denied");
                    }
                }

                conn.commit();
                LoggerUtil.info("Set screenname " + screennameId + " as primary for user " + userId);
                return screenname.withPrimary(true);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (ScreennameServiceException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to set primary screenname: " + e.getMessage());
            throw new ScreennameServiceException("Failed to set primary screenname", e);
        }
    }

    /**
     * Deletes a screenname.
     * Cannot delete the user's only screenname or primary screenname.
     *
     * @param screennameId ID of screenname to delete
     * @param userId User ID for authorization
     */
    public void deleteScreenname(int screennameId, int userId) throws ScreennameServiceException {
        try {
            // Get user's screennames
            List<Screenname> userScreennames = getScreennamesForUser(userId);

            if (userScreennames.size() <= 1) {
                throw new ScreennameServiceException("Cannot delete user's only screenname");
            }

            // Find the screenname to delete
            Screenname toDelete = userScreennames.stream()
                .filter(s -> s.id().equals(screennameId))
                .findFirst()
                .orElseThrow(() -> new ScreennameServiceException("Screenname not found or access denied"));

            if (toDelete.isPrimary()) {
                throw new ScreennameServiceException(
                    "Cannot delete primary screenname. Set another as primary first.");
            }

            // Delete screenname
            String sql = "DELETE FROM screennames WHERE id = ? AND user_id = ?";

            try (Connection conn = databaseManager.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, screennameId);
                stmt.setInt(2, userId);

                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted == 0) {
                    throw new ScreennameServiceException("Screenname not found or access denied");
                }

                LoggerUtil.info("Deleted screenname " + screennameId + " for user " + userId);
            }

        } catch (ScreennameServiceException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to delete screenname: " + e.getMessage());
            throw new ScreennameServiceException("Failed to delete screenname", e);
        }
    }

    /**
     * Deletes a screenname by ID without ownership checks (admin operation).
     * This method bypasses normal business rules and should only be used by admin operations.
     *
     * @param screennameId ID of screenname to delete
     * @throws ScreennameServiceException if deletion fails
     */
    public void deleteScreennameAdmin(int screennameId) throws ScreennameServiceException {
        try {
            String sql = "DELETE FROM screennames WHERE id = ?";

            try (Connection conn = databaseManager.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, screennameId);

                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted == 0) {
                    throw new ScreennameServiceException("Screenname not found");
                }

                LoggerUtil.info("Admin deleted screenname " + screennameId);
            }

        } catch (ScreennameServiceException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to delete screenname (admin): " + e.getMessage());
            throw new ScreennameServiceException("Failed to delete screenname", e);
        }
    }

    /**
     * Gets a specific screenname by ID, verifying it belongs to the user.
     */
    private Screenname getScreennameById(int screennameId, int userId) throws ScreennameServiceException {
        String sql = "SELECT id, user_id, screenname, password_hash, is_primary, created_at " +
                    "FROM screennames WHERE id = ? AND user_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, screennameId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // SQLite stores timestamps as TEXT in ISO format
                String createdAtStr = rs.getString("created_at");
                LocalDateTime createdAtLocal;
                if (createdAtStr != null && !createdAtStr.isEmpty()) {
                    // Parse SQLite's ISO format timestamp
                    createdAtLocal = LocalDateTime.parse(createdAtStr.replace(" ", "T"));
                } else {
                    createdAtLocal = LocalDateTime.now();
                }
                
                return Screenname.fromDatabase(
                    rs.getInt("id"),
                    rs.getInt("user_id"),
                    rs.getString("screenname"),
                    rs.getString("password_hash"),
                    rs.getBoolean("is_primary"),
                    createdAtLocal
                );
            }

            throw new ScreennameServiceException("Screenname not found or access denied");

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get screenname by ID: " + e.getMessage());
            throw new ScreennameServiceException("Database error", e);
        }
    }

    /**
     * Checks if a screenname is already taken (case-insensitive).
     */
    private boolean isScreennameTaken(String screenname) throws SQLException {
        String sql = "SELECT 1 FROM screennames WHERE LOWER(screenname) = LOWER(?)";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, screenname);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Inserts new screenname into database.
     */
    private Screenname insertScreenname(Screenname screenname) throws SQLException {
        String sql = "INSERT INTO screennames (user_id, screenname, password_hash, is_primary, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, screenname.userId());
            stmt.setString(2, screenname.screenname());
            stmt.setString(3, screenname.passwordHash());
            stmt.setBoolean(4, screenname.isPrimary());
            // SQLite expects timestamp as TEXT in ISO format
            stmt.setString(5, screenname.createdAt().toString());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating screenname failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return Screenname.fromDatabase(
                        id, screenname.userId(), screenname.screenname(),
                        screenname.passwordHash(), screenname.isPrimary(), screenname.createdAt()
                    );
                } else {
                    throw new SQLException("Creating screenname failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Exception thrown when screenname operations fail.
     */
    public static class ScreennameServiceException extends Exception {
        public ScreennameServiceException(String message) {
            super(message);
        }

        public ScreennameServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}