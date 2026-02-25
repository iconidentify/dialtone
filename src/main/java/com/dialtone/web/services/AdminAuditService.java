/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.User;
import com.dialtone.utils.JacksonConfig;
import com.dialtone.utils.LoggerUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Service for audit logging of admin actions.
 *
 * Records all administrative actions with context for compliance and security monitoring.
 * Supports automatic cleanup based on retention policy.
 */
public class AdminAuditService {

    private final DatabaseManager databaseManager;
    private final int retentionDays;
    private final int maxEntries;

    /**
     * Admin audit log entry.
     */
    public record AuditLogEntry(
        int id,
        int adminUserId,
        String adminUsername,
        String action,
        Integer targetUserId,
        String targetUsername,
        Integer targetScreennameId,
        String targetScreenname,
        String details,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
    ) {}

    /**
     * Creates AdminAuditService with configuration.
     */
    public AdminAuditService(Properties config, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;

        // Load configuration
        this.retentionDays = Integer.parseInt(config.getProperty("admin.audit.log.retention.days", "365"));
        this.maxEntries = Integer.parseInt(config.getProperty("admin.audit.log.max.entries", "100000"));

        LoggerUtil.info(String.format("AdminAuditService initialized - retention=%d days, max_entries=%d",
                       retentionDays, maxEntries));
    }

    /**
     * Logs an admin action.
     *
     * @param admin Admin user performing the action
     * @param action Action being performed (e.g., "RESET_PASSWORD", "DELETE_USER")
     * @param targetUserId Target user ID (if applicable)
     * @param targetScreennameId Target screenname ID (if applicable)
     * @param details Additional details as a map (will be JSON serialized)
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     */
    public void logAction(User admin, String action, Integer targetUserId, Integer targetScreennameId,
                         Map<String, Object> details, String ipAddress, String userAgent) {
        try {
            String detailsJson = null;
            if (details != null && !details.isEmpty()) {
                detailsJson = JacksonConfig.mapper().writeValueAsString(details);
            }

            insertAuditEntry(admin.id(), action, targetUserId, targetScreennameId,
                           detailsJson, ipAddress, userAgent);

            LoggerUtil.info(String.format("Admin action logged: %s by %s (id=%d) on target_user=%s target_screenname=%s",
                          action, admin.xUsername(), admin.id(), targetUserId, targetScreennameId));

        } catch (JsonProcessingException e) {
            LoggerUtil.error(String.format("Failed to serialize audit details for action %s by user %d: %s",
                           action, admin.id(), e.getMessage()));

            // Log without details if serialization fails
            insertAuditEntry(admin.id(), action, targetUserId, targetScreennameId,
                           "SERIALIZATION_ERROR", ipAddress, userAgent);
        }
    }

    /**
     * Convenience method to log action without details.
     */
    public void logAction(User admin, String action, String ipAddress, String userAgent) {
        logAction(admin, action, null, null, null, ipAddress, userAgent);
    }

    /**
     * Convenience method to log action on a user.
     */
    public void logUserAction(User admin, String action, int targetUserId,
                            String ipAddress, String userAgent) {
        logAction(admin, action, targetUserId, null, null, ipAddress, userAgent);
    }

    /**
     * Convenience method to log action on a screenname.
     */
    public void logScreennameAction(User admin, String action, int targetUserId, int targetScreennameId,
                                  String ipAddress, String userAgent) {
        logAction(admin, action, targetUserId, targetScreennameId, null, ipAddress, userAgent);
    }

    /**
     * Convenience method to log action with details.
     */
    public void logActionWithDetails(User admin, String action, Map<String, Object> details,
                                   String ipAddress, String userAgent) {
        logAction(admin, action, null, null, details, ipAddress, userAgent);
    }

    /**
     * Retrieves audit log entries with pagination.
     *
     * @param limit Maximum number of entries to return
     * @param offset Offset for pagination
     * @param adminUserId Filter by admin user ID (null for all)
     * @param action Filter by action type (null for all)
     * @return List of audit log entries
     */
    public List<AuditLogEntry> getAuditLog(int limit, int offset, Integer adminUserId, String action) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT
                al.id, al.admin_user_id, au.x_username as admin_username,
                al.action, al.target_user_id, tu.x_username as target_username,
                al.target_screenname_id, s.screenname as target_screenname,
                al.details, al.ip_address, al.user_agent, al.created_at
            FROM admin_audit_log al
            LEFT JOIN users au ON al.admin_user_id = au.id
            LEFT JOIN users tu ON al.target_user_id = tu.id
            LEFT JOIN screennames s ON al.target_screenname_id = s.id
            WHERE 1=1
        """);

        List<Object> parameters = new ArrayList<>();

        if (adminUserId != null) {
            sqlBuilder.append(" AND al.admin_user_id = ?");
            parameters.add(adminUserId);
        }

        if (action != null && !action.trim().isEmpty()) {
            sqlBuilder.append(" AND al.action = ?");
            parameters.add(action.trim());
        }

        sqlBuilder.append(" ORDER BY al.created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<AuditLogEntry> entries = new ArrayList<>();

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            // Set parameters
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(new AuditLogEntry(
                    rs.getInt("id"),
                    rs.getInt("admin_user_id"),
                    rs.getString("admin_username"),
                    rs.getString("action"),
                    (Integer) rs.getObject("target_user_id"),
                    rs.getString("target_username"),
                    (Integer) rs.getObject("target_screenname_id"),
                    rs.getString("target_screenname"),
                    rs.getString("details"),
                    rs.getString("ip_address"),
                    rs.getString("user_agent"),
                    rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }

        } catch (SQLException e) {
            LoggerUtil.error("Failed to retrieve audit log entries: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Gets total count of audit log entries.
     */
    public int getAuditLogCount(Integer adminUserId, String action) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(*) FROM admin_audit_log WHERE 1=1
        """);

        List<Object> parameters = new ArrayList<>();

        if (adminUserId != null) {
            sqlBuilder.append(" AND admin_user_id = ?");
            parameters.add(adminUserId);
        }

        if (action != null && !action.trim().isEmpty()) {
            sqlBuilder.append(" AND action = ?");
            parameters.add(action.trim());
        }

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            // Set parameters
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get audit log count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Cleans up old audit log entries based on retention policy.
     *
     * @return Number of entries deleted
     */
    public int cleanupOldEntries() {
        String sql = """
            DELETE FROM admin_audit_log
            WHERE created_at < datetime('now', '-' || ? || ' days')
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, retentionDays);

            int deletedCount = stmt.executeUpdate();

            if (deletedCount > 0) {
                LoggerUtil.info(String.format("Cleaned up %d old audit log entries (older than %d days)",
                              deletedCount, retentionDays));
            }

            return deletedCount;

        } catch (SQLException e) {
            LoggerUtil.error("Failed to cleanup old audit entries: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Cleans up excess entries if max limit is exceeded.
     *
     * @return Number of entries deleted
     */
    public int cleanupExcessEntries() {
        // First, get current count
        int currentCount = getAuditLogCount(null, null);

        if (currentCount <= maxEntries) {
            return 0; // No cleanup needed
        }

        int entriesToDelete = currentCount - maxEntries;

        String sql = """
            DELETE FROM admin_audit_log
            WHERE id IN (
                SELECT id FROM admin_audit_log
                ORDER BY created_at ASC
                LIMIT ?
            )
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, entriesToDelete);

            int deletedCount = stmt.executeUpdate();

            LoggerUtil.info(String.format("Cleaned up %d excess audit log entries (max=%d)",
                          deletedCount, maxEntries));

            return deletedCount;

        } catch (SQLException e) {
            LoggerUtil.error("Failed to cleanup excess audit entries: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gets audit statistics.
     */
    public Map<String, Object> getAuditStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = databaseManager.getDataSource().getConnection()) {
            // Total entries
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM admin_audit_log")) {
                ResultSet rs = stmt.executeQuery();
                stats.put("totalEntries", rs.next() ? rs.getInt(1) : 0);
            }

            // Entries by action type
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT action, COUNT(*) as count
                FROM admin_audit_log
                GROUP BY action
                ORDER BY count DESC
            """)) {
                ResultSet rs = stmt.executeQuery();
                Map<String, Integer> actionCounts = new HashMap<>();
                while (rs.next()) {
                    actionCounts.put(rs.getString("action"), rs.getInt("count"));
                }
                stats.put("actionCounts", actionCounts);
            }

            // Recent activity (last 24 hours)
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT COUNT(*) FROM admin_audit_log
                WHERE created_at > datetime('now', '-1 day')
            """)) {
                ResultSet rs = stmt.executeQuery();
                stats.put("recentActivity", rs.next() ? rs.getInt(1) : 0);
            }

        } catch (SQLException e) {
            LoggerUtil.error("Failed to get audit statistics: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Internal method to insert audit entry into database.
     */
    private void insertAuditEntry(int adminUserId, String action, Integer targetUserId,
                                 Integer targetScreennameId, String details,
                                 String ipAddress, String userAgent) {
        String sql = """
            INSERT INTO admin_audit_log
            (admin_user_id, action, target_user_id, target_screenname_id, details, ip_address, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, adminUserId);
            stmt.setString(2, action);
            stmt.setObject(3, targetUserId);
            stmt.setObject(4, targetScreennameId);
            stmt.setString(5, details);
            stmt.setString(6, ipAddress);
            stmt.setString(7, userAgent);

            stmt.executeUpdate();

        } catch (SQLException e) {
            LoggerUtil.error(String.format("Failed to insert audit entry: action=%s, admin_user=%d - %s",
                           action, adminUserId, e.getMessage()));
        }
    }
}