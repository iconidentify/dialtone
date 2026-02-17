/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Admin controller for audit log management.
 *
 * Provides admin-only endpoints for:
 * - Viewing audit log entries with filtering and pagination
 * - Getting audit statistics
 * - Triggering manual cleanup operations
 *
 * All operations require admin authentication and are themselves audit logged.
 */
public class AdminAuditController {
    private final AdminAuditService adminAuditService;
    private final AdminSecurityService adminSecurityService;
    private final CsrfProtectionService csrfService;

    public AdminAuditController(AdminAuditService adminAuditService,
                               AdminSecurityService adminSecurityService,
                               CsrfProtectionService csrfService) {
        this.adminAuditService = adminAuditService;
        this.adminSecurityService = adminSecurityService;
        this.csrfService = csrfService;
    }

    /**
     * Gets audit log entries with filtering and pagination.
     * GET /api/admin/audit
     * Query params: limit, offset, admin_user_id, action
     */
    public void getAuditLog(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse query parameters
            String limitParam = ctx.queryParam("limit");
            String offsetParam = ctx.queryParam("offset");

            int limit = Math.min(Math.max(Integer.parseInt(limitParam != null ? limitParam : "50"), 1), 500);
            int offset = Math.max(Integer.parseInt(offsetParam != null ? offsetParam : "0"), 0);

            String adminUserIdParam = ctx.queryParam("admin_user_id");
            Integer adminUserId = null;
            if (adminUserIdParam != null && !adminUserIdParam.trim().isEmpty()) {
                try {
                    adminUserId = Integer.parseInt(adminUserIdParam);
                } catch (NumberFormatException e) {
                    ctx.status(400).json(SharedErrorResponse.badRequest("Invalid admin_user_id parameter"));
                    return;
                }
            }

            String action = ctx.queryParam("action");
            if (action != null && action.trim().isEmpty()) {
                action = null;
            }

            // Get audit log entries and total count
            List<AdminAuditService.AuditLogEntry> entries = adminAuditService.getAuditLog(limit, offset, adminUserId, action);
            int totalCount = adminAuditService.getAuditLogCount(adminUserId, action);

            AuditLogResponse response = new AuditLogResponse(entries, totalCount, limit, offset);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed %d audit log entries (limit=%d, offset=%d)",
                           admin.xUsername(), entries.size(), limit, offset));

        } catch (NumberFormatException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest("Invalid limit or offset parameter"));
        } catch (Exception e) {
            LoggerUtil.error("Failed to get audit log: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve audit log"));
        }
    }

    /**
     * Gets audit log statistics.
     * GET /api/admin/audit/stats
     */
    public void getAuditStats(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Get audit statistics
            Map<String, Object> stats = adminAuditService.getAuditStatistics();

            ctx.json(new AuditStatsResponse(stats));

            LoggerUtil.debug(String.format("Admin %s viewed audit statistics", admin.xUsername()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get audit statistics: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve audit statistics"));
        }
    }

    /**
     * Triggers manual cleanup of old audit log entries.
     * POST /api/admin/audit/cleanup
     */
    public void triggerAuditCleanup(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "audit cleanup")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Trigger cleanup operations
            int deletedOld = adminAuditService.cleanupOldEntries();
            int deletedExcess = adminAuditService.cleanupExcessEntries();
            int totalDeleted = deletedOld + deletedExcess;

            CleanupResponse response = new CleanupResponse(
                "Audit log cleanup completed",
                deletedOld,
                deletedExcess,
                totalDeleted
            );

            ctx.json(response);

            // Audit log this action
            Map<String, Object> details = Map.of(
                "deleted_old_entries", deletedOld,
                "deleted_excess_entries", deletedExcess,
                "total_deleted", totalDeleted
            );
            adminAuditService.logActionWithDetails(admin, "TRIGGER_AUDIT_CLEANUP", details,
                                                 getClientIp(ctx), getClientUserAgent(ctx));

            LoggerUtil.info(String.format("Admin %s triggered audit cleanup: %d old + %d excess = %d total deleted",
                          admin.xUsername(), deletedOld, deletedExcess, totalDeleted));

        } catch (Exception e) {
            LoggerUtil.error("Failed to trigger audit cleanup: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to trigger audit cleanup"));
        }
    }

    /**
     * Gets a summary of recent admin activity.
     * GET /api/admin/audit/recent
     */
    public void getRecentActivity(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Get recent activity (last 50 entries)
            List<AdminAuditService.AuditLogEntry> recentEntries = adminAuditService.getAuditLog(50, 0, null, null);

            // Get statistics for context
            Map<String, Object> stats = adminAuditService.getAuditStatistics();

            RecentActivityResponse response = new RecentActivityResponse(recentEntries, stats);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed recent activity", admin.xUsername()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get recent activity: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve recent activity"));
        }
    }

    /**
     * Gets audit log entries for a specific user.
     * GET /api/admin/audit/user/{userId}
     */
    public void getUserAuditLog(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse user ID
            Optional<Integer> targetUserIdOpt = parsePathParamId(ctx, "userId", "user");
            if (targetUserIdOpt.isEmpty()) return;
            int targetUserId = targetUserIdOpt.get();

            // Parse query parameters
            String limitParam = ctx.queryParam("limit");
            String offsetParam = ctx.queryParam("offset");

            int limit = Math.min(Math.max(Integer.parseInt(limitParam != null ? limitParam : "100"), 1), 500);
            int offset = Math.max(Integer.parseInt(offsetParam != null ? offsetParam : "0"), 0);

            // Get audit log entries where this user was the target
            List<AdminAuditService.AuditLogEntry> entries = adminAuditService.getAuditLog(limit, offset, null, null)
                .stream()
                .filter(entry -> entry.targetUserId() != null && entry.targetUserId().equals(targetUserId))
                .toList();

            UserAuditLogResponse response = new UserAuditLogResponse(targetUserId, entries, entries.size(), limit, offset);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed audit log for user %d", admin.xUsername(), targetUserId));

        } catch (NumberFormatException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest("Invalid limit or offset parameter"));
        } catch (Exception e) {
            LoggerUtil.error("Failed to get user audit log: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve user audit log"));
        }
    }

    // Response DTOs
    public record AuditLogResponse(List<AdminAuditService.AuditLogEntry> entries, int totalCount, int limit, int offset) {}

    public record AuditStatsResponse(Map<String, Object> statistics) {}

    public record CleanupResponse(String message, int deletedOldEntries, int deletedExcessEntries, int totalDeleted) {}

    public record RecentActivityResponse(List<AdminAuditService.AuditLogEntry> recentEntries, Map<String, Object> statistics) {}

    public record UserAuditLogResponse(int userId, List<AdminAuditService.AuditLogEntry> entries, int totalCount, int limit, int offset) {}
}
