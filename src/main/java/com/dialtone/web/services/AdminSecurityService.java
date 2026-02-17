/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing admin security, role verification, and access control.
 *
 * Implements multi-layer security:
 * 1. Configuration-based admin list (X usernames)
 * 2. Database role verification
 * 3. Session-based admin tracking
 * 4. Rate limiting for admin actions
 */
public class AdminSecurityService {

    private final Set<String> adminXUsernames;
    private final boolean adminEnabled;
    private final boolean autoPromoteAdmins;
    private final int maxScreennamesOverride;
    private final int sessionTimeoutMinutes;
    private final int rateLimitPerMinute;
    private final DatabaseManager databaseManager;

    // Rate limiting: admin user ID -> last action times
    private final ConcurrentHashMap<Integer, Long> lastActionTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> actionCounts = new ConcurrentHashMap<>();

    /**
     * Creates AdminSecurityService with configuration.
     */
    public AdminSecurityService(Properties config, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;

        // Load admin configuration
        this.adminEnabled = Boolean.parseBoolean(config.getProperty("admin.enabled", "false"));
        this.autoPromoteAdmins = Boolean.parseBoolean(config.getProperty("admin.auto.promote", "true"));
        this.maxScreennamesOverride = Integer.parseInt(config.getProperty("admin.max.screennames.override", "10"));
        this.sessionTimeoutMinutes = Integer.parseInt(config.getProperty("admin.session.timeout.minutes", "30"));
        this.rateLimitPerMinute = Integer.parseInt(config.getProperty("admin.rate.limit.requests.per.minute", "60"));

        // Parse admin X usernames from configuration
        String adminUsernamesConfig = config.getProperty("admin.x.usernames", "");
        this.adminXUsernames = new HashSet<>();
        if (!adminUsernamesConfig.trim().isEmpty()) {
            Arrays.stream(adminUsernamesConfig.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .forEach(adminXUsernames::add);
        }

        LoggerUtil.info(String.format("AdminSecurityService initialized - enabled=%b, configured_admins=%d",
                       adminEnabled, adminXUsernames.size()));
    }

    /**
     * Checks if admin features are globally enabled.
     */
    public boolean isAdminEnabled() {
        return adminEnabled;
    }

    /**
     * Checks if a user is an admin based on configuration and database role.
     *
     * @param user User to check
     * @return true if user has admin privileges
     */
    public boolean isAdmin(User user) {
        if (!adminEnabled || user == null) {
            return false;
        }

        // Layer 1: Check if user is in configured admin list
        boolean isConfigAdmin = adminXUsernames.contains(user.xUsername());

        if (!isConfigAdmin) {
            return false;
        }

        // Layer 2: Check database role (auto-promote if configured)
        boolean hasAdminRole = hasAdminRole(user.id());

        if (!hasAdminRole && autoPromoteAdmins) {
            // Auto-promote user to admin role in database
            try {
                grantAdminRole(user.id(), user.id()); // Self-granted for config admins
                LoggerUtil.info(String.format("Auto-promoted user to admin role: %s (id=%d)", user.xUsername(), user.id()));
                return true;
            } catch (SQLException e) {
                LoggerUtil.error(String.format("Failed to auto-promote user to admin: %s - %s", user.xUsername(), e.getMessage()));
                return false;
            }
        }

        return hasAdminRole;
    }

    /**
     * Checks if user has admin role in database.
     */
    public boolean hasAdminRole(int userId) {
        String sql = """
            SELECT COUNT(*) FROM user_roles
            WHERE user_id = ? AND role = 'admin'
            AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            LoggerUtil.error(String.format("Failed to check admin role for user %d - %s", userId, e.getMessage()));
            return false;
        }
    }

    /**
     * Grants admin role to a user.
     *
     * @param userId User to promote
     * @param grantedBy Admin user granting the role
     */
    public void grantAdminRole(int userId, int grantedBy) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO user_roles (user_id, role, granted_by, granted_at)
            VALUES (?, 'admin', ?, CURRENT_TIMESTAMP)
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, grantedBy);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                LoggerUtil.info(String.format("Granted admin role to user %d by user %d", userId, grantedBy));
            }
        }
    }

    /**
     * Revokes admin role from a user.
     */
    public void revokeAdminRole(int userId) throws SQLException {
        String sql = "DELETE FROM user_roles WHERE user_id = ? AND role = 'admin'";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                LoggerUtil.info(String.format("Revoked admin role from user %d", userId));
            }
        }
    }

    /**
     * Checks if admin can perform action based on rate limiting.
     *
     * @param adminUserId Admin user performing action
     * @return true if action is allowed
     */
    public boolean canPerformAction(int adminUserId) {
        if (!adminEnabled) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long currentMinute = currentTime / (60 * 1000);

        // Clean up old entries (older than 1 minute)
        lastActionTimes.entrySet().removeIf(entry ->
            (currentTime - entry.getValue()) > TimeUnit.MINUTES.toMillis(1));
        actionCounts.entrySet().removeIf(entry ->
            !lastActionTimes.containsKey(entry.getKey()));

        // Check rate limit for this user
        int currentCount = actionCounts.getOrDefault(adminUserId, 0);
        Long lastActionTime = lastActionTimes.get(adminUserId);

        // Reset count if it's a new minute
        if (lastActionTime == null || (lastActionTime / (60 * 1000)) < currentMinute) {
            currentCount = 0;
        }

        if (currentCount >= rateLimitPerMinute) {
            LoggerUtil.warn(String.format("Rate limit exceeded for admin user %d: %d actions in current minute",
                          adminUserId, currentCount));
            return false;
        }

        // Update tracking
        lastActionTimes.put(adminUserId, currentTime);
        actionCounts.put(adminUserId, currentCount + 1);

        return true;
    }

    /**
     * Records an admin action for rate limiting tracking.
     */
    public void recordAdminAction(int adminUserId, String action) {
        LoggerUtil.debug(String.format("Admin action recorded: user=%d, action=%s", adminUserId, action));
    }

    /**
     * Validates that admin is allowed to perform action on target user.
     */
    public boolean canManageUser(User admin, int targetUserId) {
        if (admin == null || !isAdmin(admin)) {
            return false;
        }

        // Admins cannot manage themselves for certain destructive actions
        if (admin.id() == targetUserId) {
            LoggerUtil.warn(String.format("Admin %s attempted self-management action", admin.xUsername()));
            return false;
        }

        return canPerformAction(admin.id());
    }

    /**
     * Gets the maximum allowed screennames for admin users.
     */
    public int getMaxScreennamesForAdmin() {
        return maxScreennamesOverride;
    }

    /**
     * Gets admin session timeout in minutes.
     */
    public int getAdminSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    /**
     * Gets list of configured admin X usernames (for debugging/admin).
     */
    public Set<String> getConfiguredAdminUsernames() {
        return new HashSet<>(adminXUsernames);
    }
}