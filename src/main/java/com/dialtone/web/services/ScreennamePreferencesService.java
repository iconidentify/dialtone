/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.utils.LoggerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Service for managing screenname preferences.
 *
 * Implements lazy creation pattern - returns default preferences if no row exists.
 * This allows the system to function without requiring all screennames to have
 * explicit preferences rows in the database.
 */
public class ScreennamePreferencesService {
    private final DatabaseManager databaseManager;

    public ScreennamePreferencesService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Gets preferences for a screenname by screenname ID.
     * Returns default preferences if no row exists.
     *
     * @param screennameId The screenname ID
     * @return Preferences for the screenname (defaults if no row exists)
     * @throws ScreennamePreferencesServiceException if database error occurs
     */
    public ScreennamePreferences getPreferences(int screennameId)
            throws ScreennamePreferencesServiceException {

        String sql = "SELECT id, screenname_id, low_color_mode, created_at, updated_at " +
                    "FROM screenname_preferences WHERE screenname_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, screennameId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parsePreferencesFromResultSet(rs);
            }

            // No row exists, return defaults
            return ScreennamePreferences.defaults(screennameId);

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get preferences for screenname " + screennameId + ": " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to retrieve preferences", e);
        }
    }

    /**
     * Gets preferences by screenname string (used by protocol handlers).
     * Returns default preferences if no row exists or screenname not found.
     *
     * @param screenname The screenname string
     * @return Preferences for the screenname (defaults if no row exists)
     * @throws ScreennamePreferencesServiceException if database error occurs
     */
    public ScreennamePreferences getPreferencesByScreenname(String screenname)
            throws ScreennamePreferencesServiceException {

        String sql = """
            SELECT p.id, p.screenname_id, p.low_color_mode, p.created_at, p.updated_at
            FROM screenname_preferences p
            INNER JOIN screennames s ON p.screenname_id = s.id
            WHERE LOWER(s.screenname) = LOWER(?)
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, screenname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parsePreferencesFromResultSet(rs);
            }

            // No preferences row exists, try to get the screenname ID to return proper defaults
            Integer screennameId = getScreennameIdByName(screenname);
            if (screennameId != null) {
                return ScreennamePreferences.defaults(screennameId);
            }

            // Screenname not found - return defaults with ID 0 (protocol handler will use defaults anyway)
            LoggerUtil.debug("No screenname found for '" + screenname + "', returning default preferences");
            return ScreennamePreferences.defaults(0);

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get preferences for screenname '" + screenname + "': " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to retrieve preferences", e);
        }
    }

    /**
     * Updates preferences for a screenname (upsert pattern).
     * Creates a row if one doesn't exist.
     *
     * @param screennameId The screenname ID
     * @param lowColorMode Whether to enable low color mode
     * @return Updated preferences
     * @throws ScreennamePreferencesServiceException if update fails
     */
    public ScreennamePreferences updatePreferences(int screennameId, boolean lowColorMode)
            throws ScreennamePreferencesServiceException {

        // First, check if a row exists
        ScreennamePreferences existing = getPreferences(screennameId);

        if (existing.id() == null) {
            // No row exists, insert new one
            return insertPreferences(screennameId, lowColorMode);
        } else {
            // Row exists, update it
            return updateExistingPreferences(existing.id(), lowColorMode);
        }
    }

    /**
     * Checks if a user owns the specified screenname.
     * Used for authorization in the controller layer.
     *
     * @param userId The user ID
     * @param screennameId The screenname ID to check
     * @return true if the user owns the screenname
     * @throws ScreennamePreferencesServiceException if database error occurs
     */
    public boolean userOwnsScreenname(int userId, int screennameId)
            throws ScreennamePreferencesServiceException {

        String sql = "SELECT 1 FROM screennames WHERE id = ? AND user_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, screennameId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            LoggerUtil.error("Failed to check screenname ownership: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to verify ownership", e);
        }
    }

    /**
     * Checks if a screenname exists by ID.
     *
     * @param screennameId The screenname ID
     * @return true if the screenname exists
     * @throws ScreennamePreferencesServiceException if database error occurs
     */
    public boolean screennameExists(int screennameId) throws ScreennamePreferencesServiceException {
        String sql = "SELECT 1 FROM screennames WHERE id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, screennameId);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            LoggerUtil.error("Failed to check screenname existence: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to check screenname existence", e);
        }
    }

    /**
     * Deletes preferences for a screenname.
     * Usually not needed due to CASCADE delete, but available for explicit cleanup.
     *
     * @param screennameId The screenname ID
     * @throws ScreennamePreferencesServiceException if deletion fails
     */
    public void deletePreferences(int screennameId) throws ScreennamePreferencesServiceException {
        String sql = "DELETE FROM screenname_preferences WHERE screenname_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, screennameId);
            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                LoggerUtil.info("Deleted preferences for screenname " + screennameId);
            }

        } catch (SQLException e) {
            LoggerUtil.error("Failed to delete preferences: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to delete preferences", e);
        }
    }

    /**
     * Gets screenname ID by screenname string.
     */
    private Integer getScreennameIdByName(String screenname) throws SQLException {
        String sql = "SELECT id FROM screennames WHERE LOWER(screenname) = LOWER(?)";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, screenname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            }
            return null;
        }
    }

    /**
     * Inserts a new preferences row.
     */
    private ScreennamePreferences insertPreferences(int screennameId, boolean lowColorMode)
            throws ScreennamePreferencesServiceException {

        String sql = "INSERT INTO screenname_preferences (screenname_id, low_color_mode, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?)";

        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, screennameId);
            stmt.setBoolean(2, lowColorMode);
            stmt.setString(3, now.toString());
            stmt.setString(4, now.toString());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new ScreennamePreferencesServiceException("Creating preferences failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    LoggerUtil.info("Created preferences for screenname " + screennameId +
                                  " (lowColorMode=" + lowColorMode + ")");
                    return ScreennamePreferences.fromDatabase(id, screennameId, lowColorMode, now, now);
                } else {
                    throw new ScreennamePreferencesServiceException("Creating preferences failed, no ID obtained.");
                }
            }

        } catch (SQLException e) {
            LoggerUtil.error("Failed to insert preferences: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to create preferences", e);
        }
    }

    /**
     * Updates an existing preferences row.
     */
    private ScreennamePreferences updateExistingPreferences(int preferencesId, boolean lowColorMode)
            throws ScreennamePreferencesServiceException {

        String sql = "UPDATE screenname_preferences SET low_color_mode = ?, updated_at = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, lowColorMode);
            stmt.setString(2, now.toString());
            stmt.setInt(3, preferencesId);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new ScreennamePreferencesServiceException("Preferences not found");
            }

            // Fetch and return the updated preferences
            return getPreferencesById(preferencesId);

        } catch (SQLException e) {
            LoggerUtil.error("Failed to update preferences: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Failed to update preferences", e);
        }
    }

    /**
     * Gets preferences by preferences table ID.
     */
    private ScreennamePreferences getPreferencesById(int preferencesId)
            throws ScreennamePreferencesServiceException {

        String sql = "SELECT id, screenname_id, low_color_mode, created_at, updated_at " +
                    "FROM screenname_preferences WHERE id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, preferencesId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parsePreferencesFromResultSet(rs);
            }

            throw new ScreennamePreferencesServiceException("Preferences not found");

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get preferences by ID: " + e.getMessage());
            throw new ScreennamePreferencesServiceException("Database error", e);
        }
    }

    /**
     * Parses a ScreennamePreferences from a ResultSet row.
     */
    private ScreennamePreferences parsePreferencesFromResultSet(ResultSet rs) throws SQLException {
        String createdAtStr = rs.getString("created_at");
        String updatedAtStr = rs.getString("updated_at");

        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            createdAt = LocalDateTime.parse(createdAtStr.replace(" ", "T"));
        }
        if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
            updatedAt = LocalDateTime.parse(updatedAtStr.replace(" ", "T"));
        }

        return ScreennamePreferences.fromDatabase(
            rs.getInt("id"),
            rs.getInt("screenname_id"),
            rs.getBoolean("low_color_mode"),
            createdAt,
            updatedAt
        );
    }

    /**
     * Exception thrown when preferences operations fail.
     */
    public static class ScreennamePreferencesServiceException extends Exception {
        public ScreennamePreferencesServiceException(String message) {
            super(message);
        }

        public ScreennamePreferencesServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
